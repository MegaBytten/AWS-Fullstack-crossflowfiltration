A brief project-map of this repository:

## /PythonLambdas/
Contains two python lambda (Cloud function) scripts responsible for:
* custom user Authn/Authz using User/Token DynamoDB tables
* Uploading incoming inference API model data to a DynamoDB table

## /CrossFlowModelJavaLambda/
Contains required project structure to read and deploy the Java-based model compute
* build.gradle is Java equivalent of requirements.txt, allows project building and import/export of dependencies to AWS Lambda-supported format (.zip)
* src/main/ contains source code, compatible with deployment into a StepFunction lambda, or a Lambda exposed directly via API Gateway
* distributions/ contains the .zip export which is directly deployable to AWS Lambda

## /StepFunction/
* Contains png file to visualise the created step function architecture
* Contains .json code with removed resource numbers/account information to recreate the step function architecture 

## /APIGateway/
Contains two .vtl files:
* VTL = Velocity template language, used in mapping HTTP requests and responses
* One .vtl for pre-loading incoming requests to the specified step function ARN (included ARN for demonstration purposes)
* Other .vtl for modifying output from StepFunction response to remove sensitive billing, account, or other information

## Supplementary files
* architecturaldiagram.png showcases a high-level general overview of the full-stack architectural ecosystem
* presentation.pptx provides detailed information about the Java src/main/ encoding and algorithmic implementation of the Crossflow Filtration model