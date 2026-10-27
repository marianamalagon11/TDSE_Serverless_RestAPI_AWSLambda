#!/usr/bin/env bash
#
# Architecture B: API Gateway Lambda proxy integration.
#
# One function serves the whole API. API Gateway only routes and forwards, so there are no mapping
# templates, no integration responses and no selection patterns here: the equivalent decisions are
# in OrdersApiProxyHandler, in Java.
#
# Compare the length of this script with infrastructure/architecture-a/create-api.sh.
#
# Run from the repository root, after `mvn package`.

set -euo pipefail

REGION=${AWS_REGION:-us-east-1}
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
ROLE="arn:aws:iam::$ACCOUNT:role/LabRole"
FUNCTION=orders-api-proxy
STAGE=dev
HERE="infrastructure/architecture-b"

echo "== function =="
if aws lambda get-function --function-name "$FUNCTION" >/dev/null 2>&1; then
  aws lambda update-function-code --function-name "$FUNCTION" \
    --zip-file fileb://target/orders-api-1.0-SNAPSHOT.jar --query LastUpdateStatus --output text
else
  aws lambda create-function --function-name "$FUNCTION" --runtime java17 --role "$ROLE" \
    --handler com.escuelaing.orders.lambda.proxy.OrdersApiProxyHandler::handleRequest \
    --zip-file fileb://target/orders-api-1.0-SNAPSHOT.jar \
    --memory-size 512 --timeout 15 \
    --description "Workshop architecture B: the whole Orders API behind one proxy function" \
    --query FunctionName --output text
fi
aws lambda wait function-updated-v2 --function-name "$FUNCTION"

echo "== REST API =="
API_ID=$(aws apigateway create-rest-api --name orders-api-proxy \
  --description "Orders API, architecture B: API Gateway only routes" \
  --endpoint-configuration types=REGIONAL --query id --output text)
ROOT_ID=$(aws apigateway get-resources --rest-api-id "$API_ID" \
  --query "items[?path=='/'].id" --output text)
ORDERS_ID=$(aws apigateway create-resource --rest-api-id "$API_ID" \
  --parent-id "$ROOT_ID" --path-part orders --query id --output text)
ORDER_ID=$(aws apigateway create-resource --rest-api-id "$API_ID" \
  --parent-id "$ORDERS_ID" --path-part '{id}' --query id --output text)

# ANY: every method of the resource reaches the same function, which decides what to do with it.
# There is nothing else to configure, no template and no response mapping.
for RESOURCE in "$ORDERS_ID" "$ORDER_ID"; do
  aws apigateway put-method --rest-api-id "$API_ID" --resource-id "$RESOURCE" \
    --http-method ANY --authorization-type NONE >/dev/null
  aws apigateway put-integration --rest-api-id "$API_ID" --resource-id "$RESOURCE" \
    --http-method ANY --type AWS_PROXY --integration-http-method POST \
    --uri "arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$REGION:$ACCOUNT:function:$FUNCTION/invocations" >/dev/null
done

aws lambda add-permission --function-name "$FUNCTION" --statement-id "apigw-$API_ID" \
  --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT:$API_ID/*/*" >/dev/null

echo "== deployment =="
aws apigateway create-deployment --rest-api-id "$API_ID" --stage-name "$STAGE" \
  --description "Architecture B" --query id --output text

echo
echo "Base URL: https://$API_ID.execute-api.$REGION.amazonaws.com/$STAGE"
echo "$API_ID" > "$HERE/.api-id"
