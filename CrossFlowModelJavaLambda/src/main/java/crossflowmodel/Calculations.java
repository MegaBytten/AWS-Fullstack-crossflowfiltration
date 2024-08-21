package crossflowmodel;

import java.util.HashMap;

public class Calculations {
    public double VISCOSITY_WATER = 0.001;
    public int BOV_CASEIN_MW = 25_107;

    public Calculations(){}

    private class CrossFlowSystem {
        public double startingVolume;
        public double concentrationFactor;
        public double tmp;
        public double solutionViscosity;
        public double membraneArea;
        public double desiredVolume;
        public double requiredFiltrationVolume;
        public double currentVolume;
        public double currentTimeHours;
        public double currentFlowRate;

        double tenToTwelve = Math.pow(10, 12); // save compute

        public CrossFlowSystem(
                double startingVolume,
                double concentrationFactor,
                double tmp,
                double solutionViscosity,
                double membraneArea,

                double currentTimeHours
        ){
            System.out.println("Creating a new CrossFlowSystem object.");
            this.startingVolume = startingVolume;
            this.currentVolume = startingVolume;

            this.concentrationFactor = concentrationFactor;
            this.tmp = tmp;
            this.solutionViscosity = solutionViscosity;
            this.membraneArea = membraneArea;


            this.currentTimeHours = currentTimeHours;

            this.desiredVolume = startingVolume/concentrationFactor;
            this.requiredFiltrationVolume = startingVolume - desiredVolume;

            updateCurrentFlowRate(); // set t=0 flow rate at time of creation


            System.out.printf("CrossFlowObject created successfully: " + toString() + "\n");
        }

        @Override
        public String toString(){
            return "CrossFlowSystems Object.\n"+
                    "Starting Volume = " + this.startingVolume + ", " +
                    "Transmembrane Pressure = " + this.tmp + ", " +
                    "Solution Viscosity = " + this.solutionViscosity + ", " +
                    "Membrane Area = " + this.membraneArea + ", " +
                    "Concentration Factor = " + this.concentrationFactor + ", " +
                    "Desired Volume = " + this.desiredVolume + ", " +
                    "Current Volume = " + this.currentVolume + ", " +
                    "Current Time in Hours = " + this.currentTimeHours + ", " +
                    "Current Flow Rate = " + this.currentFlowRate;
        }

        /** Calculates and returns the permeate flow rate at specified time in hours.
         * @return returns a Double of hours required for volume to drain at the permeate flow rate respective to t=0.
         */
        private void updateCurrentFlowRate(){
            double membraneResistance = (0.13 * tenToTwelve) + (1.51 * tenToTwelve * Math.pow(this.currentTimeHours, 0.4)); // calculate time-dependent flow rate
            double membraneFluxHour = this.tmp / (this.solutionViscosity * membraneResistance); // membraneFlux m3 per hour per unit area - UNIT AGNOSTIC
            double permeateFlowRateL = membraneFluxHour * 1000.0 * this.membraneArea; // 1000 for m3 --> L

            this.currentFlowRate = permeateFlowRateL;
        }

        private void updateRunFiltrationForPeriod(double timeInHours) {
            double volumeRemoved = this.currentFlowRate * timeInHours;
            this.currentVolume = this.currentVolume - volumeRemoved;
        }

        private boolean checkFinished() {
            return this.currentVolume < this.desiredVolume;
        }

    }

    /** Primary function which calculates binned flow rates, and combines sum of estimated (binned) hours required.
     *  In order to provide scaled-accurate estimation of time, we do not want to hard-code how frequently we re-calculate the flow rate
     *  Eg. If we iterate every hour, but user-specified process will take 10,000 hours, we need 10,000 iterations.
     *  This backend processing is not fault tolerant and can quickly become extremely resource intensive
     *  Instead, we estimate the minimum number of hours required to complete the job, and bin the hours by 20
     *  Then we recalculate flow rate every bin, so if bins = 20, we only run 20 iterations.
     *  Therefore we sacrifice model accuracy, but gain cost-/compute-efficiency and scalability.
     *
     * @param startingVolume the starting volume in Litres
     * @param concentrationFactor the ratio of concentration. A concentrationFactor of 2 will create a 2x concentrated solution. Inversely proportional to starting volume.
     * @param tmp the transmembrane pressure given in Pascals
     * @param solutionViscosity the dynamic viscosity of the solution in Pascals per Second in the cross flow filtration model. Defaults to viscosity of water at 0.001 Pa s-1.
     * @param membraneArea the surface area of the membrane in Metres Squared
     * @return returns a Double of filtration hours required to reach specified concentration.
     */
    public HashMap<String, Double> calculateHours(
            double startingVolume,
            double concentrationFactor,
            double tmp,
            double solutionViscosity,
            double membraneArea
    ){
        //logging for traceability
        System.out.println("Algorithmic calculateHours called.");

        // create response HashMap for early exit use
        HashMap<String, Double> response = new HashMap<>();

        // SAFETY CHECKS:
        // check if concentration factor is <1 = dilution, not concentration. Exit.
        if (concentrationFactor < 1){
            response.put("statusCode", 1.0); // failure
            return response;
        }

        // then check if concentration factor == 1, desired concentration already reached.
        if (concentrationFactor == 1){
            response.put("statusCode", 0.0); // success
            response.put("hours", 0.0);
            return response;
        }

        // No safety checks exited. Create systemsObject for easier data storage across multiple functions
        CrossFlowSystem crossflowsystemObj = new CrossFlowSystem(
                startingVolume,
                concentrationFactor,
                tmp,
                solutionViscosity,
                membraneArea,
                0
        );


        /** !IMPORTANT Part 1. of Algorithm:
         *  Fine-tuned, high resolution during the EARLY Stage of the filtration
         *  Important to run lots of iterations within first ~20 hours, as filtration rate rapidly declines
         */
        double alg1NumHours = 20;
        double alg1NumIterations = 50;
        double alg1Scale = alg1NumHours/ alg1NumIterations;

        for (int i =0; i<alg1NumIterations;i++) {
            crossflowsystemObj.currentTimeHours = i*alg1Scale; // iteratively increment time in hours
            crossflowsystemObj.updateCurrentFlowRate(); // iteratively update flow rate
            crossflowsystemObj.updateRunFiltrationForPeriod(alg1Scale); // run remove volume function for set time every iteration.

            if (crossflowsystemObj.checkFinished()) {
                System.out.println("crossflow Filtration completed.");
                response.put("statusCode", 0.0); // success
                response.put("hours", crossflowsystemObj.currentTimeHours);
                return response;
            }
        }

        System.out.printf("First 20 hours volume removed: %f\n", crossflowsystemObj.startingVolume-crossflowsystemObj.currentVolume);

        // if this is reached, 20 hours have iterated through updating cross flow filtration 50 times
        // and volume has not fallen below desiredVolume.

        /** !IMPORTANT Part 2. of Algorithm:
         * Now that the volatile, high-resolution first 20 hours have been iterated through,
         * we need a different approach to deal with the remaining hours.
         * A project can span several thousand hours, and we do not want to risk our compute iterating
         * 50 times every 20 hours.
         *
         * Instead, of simply using the t=20 flow rate, we use this value to predict a vague time remaining
         * Then we can iterate every 1%-100% of this time remaining to proportionately scale how frequently -
         * we recalculate our flow rates.
         */

        // start with creating an estimate of how much time is remaining at t=20
        double remainingVolume = crossflowsystemObj.currentVolume - crossflowsystemObj.desiredVolume;

        // FAULT TOLERANT: base estimated time remaining based on currentFlowRate
        double estimatedTimeRemaining = remainingVolume / crossflowsystemObj.currentFlowRate;

        // Establish "MAGIC NUMBER" iterations for roughly how many times we want to update diminishing flow rate beyond t=20
        double alg2NumIterations = 50;
        double alg2Scale = estimatedTimeRemaining/ alg2NumIterations; // update scale for similar use
        System.out.printf("Estimated time remaining: %f, scale (flowrate refresh frequency): %f\n", estimatedTimeRemaining, alg2Scale);

        // now our loop function acts the exact same way, but our scale should be much larger now.
        for (int i =0; i<alg2NumIterations; i++) {
            crossflowsystemObj.currentTimeHours = crossflowsystemObj.currentTimeHours+alg2Scale; // iteratively increment time in hours
            crossflowsystemObj.updateCurrentFlowRate(); // iteratively update flow rate
            crossflowsystemObj.updateRunFiltrationForPeriod(alg2Scale); // run remove volume function for set time

            if (crossflowsystemObj.checkFinished()) {
                System.out.println("crossflow Filtration completed.");
                response.put("statusCode", 0.0); // success
                response.put("hours", crossflowsystemObj.currentTimeHours);
                return response;
            }
        }

        // We have now done 50 (under 20h) + 50 (over 20h, likely into thousands of hours) iterations
        // Now we can accurately take the current flow rate and calculate the remaining time
        remainingVolume = crossflowsystemObj.currentVolume - crossflowsystemObj.desiredVolume;
        double remainingTime = remainingVolume / crossflowsystemObj.currentFlowRate;

        // send response
        response.put("statusCode", 0.0); // success
        response.put("hours", crossflowsystemObj.currentTimeHours+remainingTime);

        return response;
    }
}
