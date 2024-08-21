package crossflowmodel;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class HandlerStepFunction {

    /**
     * GLOBAL VARS SECTION
     */
    static final double VISCOSITY_WATER = 0.001;
    static final int BOV_CASEIN_MW = 25_107;
    static final String[] REQUIRED_PARAMS = {
            "volumeStart",
            "tmp",
            "membraneArea",
            "molWeightCutOff",
            "concFactor"
            // "concentrationStart", // dont use anywhere in the model
    };
    Calculations calc = new Calculations();

    // create response object
    // Loading DATA into JSON for response
    Map<String, Object> response = new HashMap<>();
    String jsonResponse = "";



    /** TODO my notes suggest that the handler receives data as MAP data and not JSON data
     * {statusCode=200, status=0, statusString=success, body={"volumeStart": "1000", "tmp": "100000", "membraneArea": "10", "molWeightCutOff": "2500", "concFactor": "2"}}
     * the body={} is JSON which will require parsing, but the should definitely be loaded into hashmap first
     * probably need lots of safety code to see whats going on?
     */

    public Object handleRequest(Object event, Context context) {

        // object event in Step Function gets passed in as a JSON String --> Can parse using Jackson
        String eventString = event.toString();
        System.out.println(eventString);

        // incoming payload is formatted as HASHMAP data store --> NOT JSON STRING
        // because data is not quotation mark enclosed not {"statusCode" but {statusCode
        // eg. {statusCode=200, status=0, statusString=success, body={"volumeStart": "1000", "tmp": "100000", "membraneArea": "10", "molWeightCutOff": "2500", "concFactor": "2"}}
        // String[] eventKeyValuePairs = event.toString().split(","); // cannot simply parse, because "body={}" has commas
//            eventKeyValuePairs[i] = eventKeyValuePairs[i].replaceAll("[{} ]", " "); // remove curly braces and whitespace

        // Extract the "body" part of the input string
        String bodyKey = "body=";
        int bodyStartIndex = eventString.indexOf(bodyKey) + bodyKey.length();
        String bodyString = eventString.substring(bodyStartIndex).trim(); // extract body {}} string

        // removes trailing/starting whitespace ensures string is not null
        if(bodyString == null || bodyString.trim().isEmpty()) {
            System.out.println("Error 500: String extraction of body= unsuccessful.");
            return "{\"modelpredictionstatus\":1}";
        }

        // now parse body data - which is JSON Formatted
        String jsonBodyString = bodyString.substring(0, bodyString.length() - 1); // remove last char should be }
        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, Double> modelParams = new HashMap<>();
        boolean correctParams = true;
        System.out.println("Attempting to JSON-parse bodyDataString: " + jsonBodyString);

        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(jsonBodyString);
            System.out.println(jsonNode.toString()); // prints the same with or without .toString()

            // iterate through jsonNode tree and attempt to load data into
            for (String param : REQUIRED_PARAMS) {
                // Check if field is null
                if (
                    !jsonNode.has(param) ||
                    !jsonNode.hasNonNull(param) ||
                    jsonNode.get(param) == null ||
                    jsonNode.get(param).toString().equalsIgnoreCase("")
                ) {
                    correctParams = false;
                    System.out.println("ERROR Param not found: " + param);
                    break;
                }

                JsonNode field = jsonNode.get(param);
                System.out.println(param + " " + field);

                // safely convert value to double or exit
                try {
                    modelParams.put(
                            param,
                            Double.parseDouble(field.asText()) // extract the natural value. asString() surrounds in "" quotes
                    );
                } catch(Exception e) {
                    System.out.println("ERROR Could not safely convert param to double:" + param);
                    e.printStackTrace();
                    correctParams = false;
                    break;
                }
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        //EXIT EARLY IF INCORRECT PARAMS
        if (!correctParams){
            return "ERROR 500: Incorrect parameters provided.";
        }

        //EXIT EARLY
        // check if molecular weight cut off is smaller than bovein casein MW, if not return failure
        if (modelParams.get("molWeightCutOff") >= calc.BOV_CASEIN_MW ){
            return "Error 400: Bad request, Bovine Casein solution requires membrane MWCO of less than " + calc.BOV_CASEIN_MW;
        }

        /* TODO - Rework tokenauth.py script to pass on solution viscosity IF PROVIDED
            Currently using hardcoded viscosity
         */
        modelParams.put("solutionViscosity", calc.VISCOSITY_WATER);

        // beyond this point, all params satisfied --> calculate hours
        System.out.println("Successfully captured all model parameters: " + modelParams +"\nRunning hours calculation.");

        HashMap<String, Double> results = calc.calculateHours(
                modelParams.get("volumeStart"), // func param[0] =  startingVolume
                modelParams.get("concFactor"), // func param[1] = concentrationFactor
                modelParams.get("tmp"), // func param[2] = tmp
                modelParams.get("solutionViscosity"), // func param[3] = solutionViscosity
                modelParams.get("membraneArea") // func param[4] = membraneArea
        );
        // check that status was OK
        if (results.get("statusCode") != 0){ // failure
            System.out.println("CalculateHours response statusCode != 0. Problematic response.");

            if (results.get("statusCode") == 1.0) { // statusCode 1 = concentrationfactor < 1
                response.put("statusCode", 400);
                response.put("errorMessage", "Bad Request. Concentration factor cannot be <1.");
            } else {
                response.put("statusCode", 500);
                jsonResponse = "Unknown Server error: Algorithmic statusCode !=0.";
            }
        } else { // no failure, get filtration hours
            System.out.println("CalculateHours response statusCode == 0. Healthy response.");
            System.out.printf("Filtration Hours Required: %f", results.get("hours"));
            response.put("filtrationHours", results.get("hours"));
            response.put("statusCode", 200);
        }

        // attempt to convert Hashmap --> JSON, set status code if fail/success
        try {
            jsonResponse = new ObjectMapper().writeValueAsString(response);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            jsonResponse = "Internal Server Error. Failed to load response payload to JSON Data: " + e.getMessage();
        }

        return jsonResponse;
    }
}