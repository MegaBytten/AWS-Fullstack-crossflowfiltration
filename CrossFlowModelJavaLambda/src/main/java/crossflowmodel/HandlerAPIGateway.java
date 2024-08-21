package crossflowmodel;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HandlerAPIGateway implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    /**
     * GLOBAL VARS SECTION
     */


    // list of all required params
    static final String[] REQUIRED_PARAMS = {
            "volumeStart",
            "tmp",
            "membraneArea",
            "molWeightCutOff",
            "concFactor"
            // "concentrationStart", // dont use anywhere in the model
    };
    private static final Calculations calculator = new Calculations();


    /**
    Example Incoming Request from PostMAN:
    {resource:
        /crossflowmodelinference,
        path: /crossflowmodelinference,
        httpMethod: POST,
        headers:
            {Accept= * SLASH *, // cannot put / here as it terminates comment!!!
            Accept-Encoding=gzip,
            deflate,
            br,
            Cache-Control=no-cache,
            Content-Type=text/plain,
            Host=fsl4trl3ej.execute-api.eu-west-2.amazonaws.com,
            Postman-Token=aa650fad-908e-494d-bb93-00fbb01db178,
            User-Agent=PostmanRuntime/7.41.1,
            X-Amzn-Trace-Id=Root=1-66c35d6b-27419ba53324b84d639631f4,
            X-Forwarded-For=31.205.219.152,
            X-Forwarded-Port=443,
            X-Forwarded-Proto=https},
        multiValueHeaders:
                {Accept=[* SLASH *], // SAME PROBLEM HERE! SLASH
                Accept-Encoding=[gzip,
                deflate, br], Cache-Control=[no-cache],
                Content-Type=[text/plain],
                Host=[fsl4trl3ej.execute-api.eu-west-2.amazonaws.com],
                Postman-Token=[aa650fad-908e-494d-bb93-00fbb01db178],
                User-Agent=[PostmanRuntime/7.41.1],
                X-Amzn-Trace-Id=[Root=1-66c35d6b-27419ba53324b84d639631f4],
                X-Forwarded-For=[31.205.219.152],
                X-Forwarded-Port=[443],
                ...
                X-Forwarded-Proto=[https]},
        body: {"volumeStart":"1_000", "tmp":"100000", "membraneArea":10, "molWeightCutOff":"1000000000", "concFactor":2},isBase64Encoded: false}

    */


    /**
     * Entry point handler for incoming Lambda Requests (API Gateway).
     */
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        // First thing to do is create response:
        // set headers of response type
        // more detail here: https://stackoverflow.com/questions/23714383/what-are-all-the-possible-values-for-http-content-type-header
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        headers.put("Access-Control-Allow-Origin","*"); // CORS HEADER:
        headers.put("Access-Control-Allow-Headers","Content-Type"); // CORS HEADER

        // create response event object with headers Hashmap
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);


        // first need to check API Gateway Request Event was sent --> data submission through APIGateway only
        if (input == null){
            response.setStatusCode(400);
            return response.withBody("Bad request. No APIGateway Data or body.");
        }

        // print the incoming response (input)
        System.out.println(input);
        System.out.println(input.getBody());

        // Create an ObjectMapper instance
        ObjectMapper objectMapper = new ObjectMapper();


        // initialise failed body var
        String body = "No Data.";

        try {
            // Parse the JSON body to a JsonNode object
            JsonNode jsonNode = objectMapper.readTree(input.getBody());

            // Access specific fields in the JSON (example: assuming your JSON has a "hello" field)



            boolean correctParams = true;
            HashMap<String, Double> modelInputParams = new HashMap<>();

            for (String param : REQUIRED_PARAMS) {
                JsonNode field = jsonNode.get(param);

                // Check if field is null
                if (field == null || field.isNull()) {
                    correctParams = false;
                    break;
                }

                // safely convert value to double or exit
                try {
                    modelInputParams.put(
                            param,
                            Double.parseDouble(field.toString())
                    );
                } catch(Exception e) {
                    e.printStackTrace();
                    correctParams = false;
                    break;
                }
            } // end of for param : modelInputParams loop


            // EXIT EARLY
            // not all required parameters given --> return bad response from Lambda
            if (!correctParams){
                response.setStatusCode(400);
                return response.withBody("Bad request, not all model parameters provided in numeric format");
            }


            // EXIT EARLY
            // check if molecular weight cut off is smaller than bovein casein MW, if not return failure
            if (modelInputParams.get("molWeightCutOff") >= calculator.BOV_CASEIN_MW ){
                response.setStatusCode(400);
                return response.withBody(String.format(
                        "Bad request, Bovine Casein solution requires membrane MWCO of less than %d.", calculator.BOV_CASEIN_MW
                ));
            }

            // check if solution viscosity was provided, if so assign it in hashmap, otherwise assign viscosity of water as default
            JsonNode field = jsonNode.get("solutionViscosity");
            if (field == null || field.isNull() || !field.isNumber() ) { // Check if field is null or non-numeric
                modelInputParams.put("solutionViscosity", calculator.VISCOSITY_WATER);
            } else {
                modelInputParams.put("solutionViscosity", field.asDouble());
            }


            // beyond this, model parameters satisfied - and loaded into hashmap

            // TODO - reworked the return of calculator.calculateHours(). Need to update this remaining code and printing.
            HashMap<String, Double> results = calculator.calculateHours(
                    modelInputParams.get("volumeStart"), // func param[0] =  startingVolume
                    modelInputParams.get("concFactor"), // func param[1] = concentrationFactor
                    modelInputParams.get("tmp"), // func param[2] = tmp
                    modelInputParams.get("solutionViscosity"), // func param[3] = solutionViscosity
                    modelInputParams.get("membraneArea") // func param[4] = membraneArea
            );

            // print for logging
            System.out.println("Calculate Hours statusCode: " + results.get("statusCode"));
            System.out.printf("Filtration Hours Required: %f\n", results.get("hours"));

            // Loading DATA into JSON for response
            Map<String, Object> data = new HashMap<>();
            data.put("filtrationHours", results.get("hours"));

            // attempt to convert Hashmap --> JSON, set status code if fail/success
            try {
                body = new ObjectMapper().writeValueAsString(data);
                response.setStatusCode(200);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                response.setStatusCode(500);
                body = "Internal Server Error. Failed to load JSON Data: " + e.getMessage();

            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusCode(400);
            body = "Bad Request, data not provided as JSON String with required parameters.";
        }

        // return the response with data based on JSON try/catch
        return response
                .withBody(body);
    }
}
