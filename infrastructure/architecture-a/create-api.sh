#!/usr/bin/env bash
#
# Architecture A: API Gateway custom (non-proxy) integration.
#
# Creates the REST API, its resources and methods, and wires each method to the Lambda function
# that implements the operation. Every HTTP concern lives here, in configuration: routing,
# parameter extraction, request transformation, status codes and error mapping.
#
# Run from the repository root, after `mvn package` and after the three functions exist.

set -euo pipefail

REGION=${AWS_REGION:-us-east-1}
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
API_NAME=orders-api-custom
STAGE=dev
# Paths stay relative to the repository root: the AWS CLI on Windows cannot read the
# /c/... style paths that Git Bash produces.
HERE="infrastructure/architecture-a"
TEMPLATES="$HERE/mapping-templates"

lambda_uri() {
  echo "arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$REGION:$ACCOUNT:function:$1/invocations"
}

echo "== REST API =="
API_ID=$(aws apigateway create-rest-api \
  --name "$API_NAME" \
  --description "Orders API, architecture A: API Gateway transforms every request" \
  --endpoint-configuration types=REGIONAL \
  --query id --output text)

ROOT_ID=$(aws apigateway get-resources --rest-api-id "$API_ID" \
  --query "items[?path=='/'].id" --output text)

ORDERS_ID=$(aws apigateway create-resource --rest-api-id "$API_ID" \
  --parent-id "$ROOT_ID" --path-part orders --query id --output text)

ORDER_ID=$(aws apigateway create-resource --rest-api-id "$API_ID" \
  --parent-id "$ORDERS_ID" --path-part '{id}' --query id --output text)

echo "api=$API_ID  /orders=$ORDERS_ID  /orders/{id}=$ORDER_ID"

# ---------------------------------------------------------------------------
# GET /orders/{id}  ->  orders-get-order
# The template turns the path parameter into {"id": "ORD-42"}.
# ---------------------------------------------------------------------------
echo "== GET /orders/{id} =="
aws apigateway put-method --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method GET --authorization-type NONE \
  --request-parameters "method.request.path.id=true" >/dev/null

aws apigateway put-integration --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method GET --type AWS --integration-http-method POST \
  --uri "$(lambda_uri orders-get-order)" \
  --passthrough-behavior WHEN_NO_MATCH \
  --request-templates "file://$TEMPLATES/get-order-request.json" >/dev/null

for code in 200 400 404 500; do
  aws apigateway put-method-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
    --http-method GET --status-code $code \
    --response-models '{"application/json":"Empty"}' >/dev/null
done

# No selection pattern means "default response": the one used when the function succeeds.
aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method GET --status-code 200 >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method GET --status-code 404 --selection-pattern ".*ORDER_NOT_FOUND.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method GET --status-code 400 --selection-pattern ".*INVALID_ORDER.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method GET --status-code 500 --selection-pattern ".*INTERNAL_ERROR.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

# ---------------------------------------------------------------------------
# GET /orders?customerId=...  ->  orders-find-orders-by-customer
# ---------------------------------------------------------------------------
echo "== GET /orders?customerId= =="
aws apigateway put-method --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method GET --authorization-type NONE \
  --request-parameters "method.request.querystring.customerId=true" >/dev/null

aws apigateway put-integration --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method GET --type AWS --integration-http-method POST \
  --uri "$(lambda_uri orders-find-orders-by-customer)" \
  --passthrough-behavior WHEN_NO_MATCH \
  --request-templates "file://$TEMPLATES/find-orders-request.json" >/dev/null

for code in 200 400 500; do
  aws apigateway put-method-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
    --http-method GET --status-code $code \
    --response-models '{"application/json":"Empty"}' >/dev/null
done

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method GET --status-code 200 >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method GET --status-code 400 --selection-pattern ".*INVALID_ORDER.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method GET --status-code 500 --selection-pattern ".*INTERNAL_ERROR.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

# ---------------------------------------------------------------------------
# POST /orders  ->  orders-create-order
# The body already has the shape the function expects, so it is passed through unchanged.
# passthrough-behavior NEVER makes a request with another content type fail with 415.
# ---------------------------------------------------------------------------
echo "== POST /orders =="
aws apigateway put-method --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method POST --authorization-type NONE >/dev/null

aws apigateway put-integration --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method POST --type AWS --integration-http-method POST \
  --uri "$(lambda_uri orders-create-order)" \
  --passthrough-behavior NEVER \
  --request-templates "file://$TEMPLATES/create-order-request.json" >/dev/null

for code in 201 400 500; do
  aws apigateway put-method-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
    --http-method POST --status-code $code \
    --response-models '{"application/json":"Empty"}' >/dev/null
done

# A successful creation answers 201, which is decided here and not in Java.
aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method POST --status-code 201 >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method POST --status-code 400 --selection-pattern ".*INVALID_ORDER.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDERS_ID" \
  --http-method POST --status-code 500 --selection-pattern ".*INTERNAL_ERROR.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

# ---------------------------------------------------------------------------
# Permissions: API Gateway must be allowed to invoke each function.
# ---------------------------------------------------------------------------
echo "== permissions =="
add_permission() {
  aws lambda add-permission --function-name "$1" --statement-id "$2" \
    --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT:$API_ID/*/*" >/dev/null
}
add_permission orders-get-order "apigw-$API_ID-get"
add_permission orders-find-orders-by-customer "apigw-$API_ID-find"
add_permission orders-create-order "apigw-$API_ID-create"

# ---------------------------------------------------------------------------
echo "== deployment =="
aws apigateway create-deployment --rest-api-id "$API_ID" --stage-name "$STAGE" \
  --description "Architecture A" --query id --output text

echo
echo "Base URL: https://$API_ID.execute-api.$REGION.amazonaws.com/$STAGE"
echo "$API_ID" > "$HERE/.api-id"
