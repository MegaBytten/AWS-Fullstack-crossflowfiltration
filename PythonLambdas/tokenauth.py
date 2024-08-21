###############################################################
######################## Configuration ########################
###############################################################
DYNAMO_TOKENS_TABLE_NAME = "" # insert your DynamoDB TOKENS table name

DYNAMO_TOKENS_USERNAME = "username" # insert name of your Column Name storing TOKEN IDENTIFIERS (username, UID, email) 
DYNAMO_TOKENS_TOKEN = "token" # insert name of your Column Name storing TOKEN tokens

DETAILED_LOGGING = True # Change this if you only want logs reporting user sign in + token success, or failure.

###############################################################
########################  Source Code  ########################
###############################################################

import boto3 # AWS handler
import logging
import json


# # # # GLOBAL VARS # # # #
logger = logging.getLogger()
logger.setLevel(logging.INFO)

DYNAMODB = boto3.client('dynamodb')
TOKEN = None

def lambda_handler(event, context):
    # # instantiate globals # #
    global DYNAMODB, TOKEN
    
    # Check if Model parameters are satisfied
    required_model_params = [
        # Required Authn parameters
        "username",
        "token",
        
        # Required Model parameters
        "volumeStart",
        "tmp",
        "membraneArea",
        "molWeightCutOff",
        "concFactor",
    ]
    
    # EXIT IF MISSING PARAMS
    missing_params = [param for param in required_model_params if param not in event]
    if missing_params:
        print(f"400 - Bad request. Not all parameters provided. Missing params = {missing_params}")
        return {
            'statusCode': 400,
            'status': 1,
            'statusString': 'failed',
            'body': "Error 400, Bad request. Please review documentation for required parameters.",
            'headers': {
                'Access-Control-Allow-Origin': '*', 
                'Access-Control-Allow-Credentials': True, 
                'Content-Type': 'application/json',
            },
        }
    
    # get user's login data from API Gateway
    token_attempt = event.get("token")
    username = event.get("username")
    
    if DETAILED_LOGGING: logger.info(f"User: {username} attempting to auth with Token.")
    
    # https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/dynamodb/client
    data = DYNAMODB.get_item(
        TableName=DYNAMO_TOKENS_TABLE_NAME,
        Key={DYNAMO_TOKENS_USERNAME:{'S':username}}
    )
    
    # EXIT IF FAILED AUTH
    if 'Item' not in data:
        logger.warning(f"User: {username} not found in Token DB.")
        return {
            'statusCode': 403,
            'status': 1,
            'statusString': 'failed',
            'body': "Error 403, user authentication failed.",
            'headers': {
                'Access-Control-Allow-Origin': '*', 
                'Access-Control-Allow-Credentials': True, 
                'Content-Type': 'application/json',
            },
        }
    
    # Get TOKEN from DB to compare
    TOKEN = data['Item'][DYNAMO_TOKENS_TOKEN]['S'] # get String value of TOKEN from dynamoDB response

    # Attempt sign in
    if token_attempt != TOKEN:
        logger.warning(f"User: {username} failed to token authorise.")
        return {
            'statusCode': 403,
            'status': 1,
            'statusString': 'failed',
            'body': "Error 403, user authentication failed.",
            'headers': {
                'Access-Control-Allow-Origin': '*', 
                'Access-Control-Allow-Credentials': True, 
                'Content-Type': 'application/json',
            },
        }
    
    # Successful Sign in - loading and sending data to next step in step function
    data = {
        "username": username,
        "volumeStart": event.get("volumeStart"),
        "tmp": event.get("tmp"),
        "membraneArea": event.get("membraneArea"),
        "molWeightCutOff": event.get("molWeightCutOff"),
        "concFactor": event.get("concFactor")
    }
    logger.info(f"User: {username} successfully signed in. Passing data: {json.dumps(data)} to next stage.")
    
    
    return {
            'statusCode': 200,
            'status': 0,
            'statusString': 'success',
            'body': json.dumps(data),
        }
