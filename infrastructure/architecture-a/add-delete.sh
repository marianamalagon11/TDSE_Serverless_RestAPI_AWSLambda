#!/usr/bin/env bash
#
# Architecture A: everything that adding DELETE /orders/{id} costs in configuration.
#
# Compare with architecture B, where the same operation needed no AWS change at all, because the
# resource is already declared with an ANY method.
#
# Run from the repository root, after `mvn package` and after create-api.sh.

set -euo pipefail

REGION=${AWS_REGION:-us-east-1}
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
ROLE="arn:aws:iam::$ACCOUNT:role/LabRole"
FUNCTION=orders-delete-order
HERE="infrastructure/architecture-a"
TEMPLATES="$HERE/mapping-templates"
API_ID=$(cat "$HERE/.api-id")

echo "== 1. a fourth Lambda function =="
if aws lambda get-function --function-name "$FUNCTION" >/dev/null 2>&1; then
  aws lambda update-function-code --function-name "$FUNCTION" \
    --zip-file fileb://target/orders-api-1.0-SNAPSHOT.jar --query LastUpdateStatus --output text
else
  aws lambda create-function --function-name "$FUNCTION" --runtime java17 --role "$ROLE" \
    --handler com.escuelaing.orders.lambda.DeleteOrderHandler::handleRequest \
    --zip-file fileb://target/orders-api-1.0-SNAPSHOT.jar \
    --memory-size 512 --timeout 15 \
    --description "Workshop architecture A: delete an order" \
    --query FunctionName --output text
fi
aws lambda wait function-updated-v2 --function-name "$FUNCTION"

ORDER_ID=$(aws apigateway get-resources --rest-api-id "$API_ID" \
  --query "items[?path=='/orders/{id}'].id" --output text)

echo "== 2. the method =="
aws apigateway put-method --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method DELETE --authorization-type NONE \
  --request-parameters "method.request.path.id=true" >/dev/null

echo "== 3. the integration and its request template =="
aws apigateway put-integration --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method DELETE --type AWS --integration-http-method POST \
  --uri "arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$REGION:$ACCOUNT:function:$FUNCTION/invocations" \
  --passthrough-behavior WHEN_NO_MATCH \
  --request-templates "file://$TEMPLATES/get-order-request.json" >/dev/null

echo "== 4. four method responses =="
for code in 204 400 404 500; do
  aws apigateway put-method-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
    --http-method DELETE --status-code $code >/dev/null
done

echo "== 5. four integration responses =="
# The function returns void, so the runtime answers with the literal payload `null`. An empty
# response template is what keeps that out of the body of a 204.
aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method DELETE --status-code 204 \
  --response-templates "file://$TEMPLATES/empty-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method DELETE --status-code 404 --selection-pattern ".*ORDER_NOT_FOUND.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method DELETE --status-code 400 --selection-pattern ".*INVALID_ORDER.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

aws apigateway put-integration-response --rest-api-id "$API_ID" --resource-id "$ORDER_ID" \
  --http-method DELETE --status-code 500 --selection-pattern ".*INTERNAL_ERROR.*" \
  --response-templates "file://$TEMPLATES/error-response.json" >/dev/null

echo "== 6. the permission =="
aws lambda add-permission --function-name "$FUNCTION" --statement-id "apigw-$API_ID-delete" \
  --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT:$API_ID/*/*" >/dev/null

echo "== 7. a new deployment =="
aws apigateway create-deployment --rest-api-id "$API_ID" --stage-name dev \
  --description "Architecture A with DELETE" --query id --output text
