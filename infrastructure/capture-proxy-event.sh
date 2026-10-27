#!/usr/bin/env bash
#
# Captures the real event that API Gateway sends to a Lambda in a proxy integration.
#
# Deploys EventEchoHandler behind proxy routes, sends a few requests, saves what comes back into
# docs/proxy-events/, and removes the throwaway APIs and function again. Nothing here is part of
# the API: it is the instrument used to document section 11 with evidence rather than with quotes
# from the documentation.
#
# Two APIs are built on purpose, because the shape of the event depends on how the resource is
# declared:
#   1. declared resources, /orders and /orders/{id}, which is what architecture B uses
#   2. a single greedy route, /{proxy+}, for comparison
#
# Run from the repository root, after `mvn package`.

set -euo pipefail

REGION=${AWS_REGION:-us-east-1}
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
ROLE="arn:aws:iam::$ACCOUNT:role/LabRole"
FUNCTION=orders-event-echo
OUT=docs/proxy-events

mkdir -p "$OUT"

echo "== echo function =="
aws lambda create-function --function-name "$FUNCTION" --runtime java17 --role "$ROLE" \
  --handler com.escuelaing.orders.lambda.EventEchoHandler::handleRequest \
  --zip-file fileb://target/orders-api-1.0-SNAPSHOT.jar \
  --memory-size 512 --timeout 15 \
  --description "Diagnostic: echoes the API Gateway proxy event" \
  --query FunctionName --output text
aws lambda wait function-active-v2 --function-name "$FUNCTION"

wire_proxy() { # api_id resource_id
  aws apigateway put-method --rest-api-id "$1" --resource-id "$2" \
    --http-method ANY --authorization-type NONE >/dev/null
  # AWS_PROXY: no mapping template, no integration response, no method response.
  aws apigateway put-integration --rest-api-id "$1" --resource-id "$2" \
    --http-method ANY --type AWS_PROXY --integration-http-method POST \
    --uri "arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$REGION:$ACCOUNT:function:$FUNCTION/invocations" >/dev/null
}

publish() { # api_id
  aws lambda add-permission --function-name "$FUNCTION" --statement-id "apigw-$1" \
    --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT:$1/*/*" >/dev/null
  aws apigateway create-deployment --rest-api-id "$1" --stage-name dev --query id --output text >/dev/null
}

# ---------------------------------------------------------------------------
echo "== API 1: declared resources =="
API1=$(aws apigateway create-rest-api --name orders-api-echo-declared \
  --endpoint-configuration types=REGIONAL --query id --output text)
ROOT1=$(aws apigateway get-resources --rest-api-id "$API1" --query "items[?path=='/'].id" --output text)
ORDERS=$(aws apigateway create-resource --rest-api-id "$API1" --parent-id "$ROOT1" \
  --path-part orders --query id --output text)
ORDER=$(aws apigateway create-resource --rest-api-id "$API1" --parent-id "$ORDERS" \
  --path-part '{id}' --query id --output text)
wire_proxy "$API1" "$ORDERS"
wire_proxy "$API1" "$ORDER"
publish "$API1"

aws apigateway get-resources --rest-api-id "$API1" --query "items[].path" --output text

BASE1="https://$API1.execute-api.$REGION.amazonaws.com/dev"
curl -s "$BASE1/orders/ORD-42" > "$OUT/get-order-by-id.json"
curl -s "$BASE1/orders?customerId=CUST-42&limit=10" > "$OUT/get-orders-by-customer.json"
curl -s -X POST "$BASE1/orders" \
  -H "Content-Type: application/json" \
  -H "X-Workshop: serverless" \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}' \
  > "$OUT/post-order.json"
curl -s -X DELETE "$BASE1/orders/ORD-42" > "$OUT/delete-order.json"

# ---------------------------------------------------------------------------
echo "== API 2: greedy route =="
API2=$(aws apigateway create-rest-api --name orders-api-echo-greedy \
  --endpoint-configuration types=REGIONAL --query id --output text)
ROOT2=$(aws apigateway get-resources --rest-api-id "$API2" --query "items[?path=='/'].id" --output text)
PROXY=$(aws apigateway create-resource --rest-api-id "$API2" --parent-id "$ROOT2" \
  --path-part '{proxy+}' --query id --output text)
wire_proxy "$API2" "$PROXY"
publish "$API2"

BASE2="https://$API2.execute-api.$REGION.amazonaws.com/dev"
curl -s "$BASE2/orders/ORD-42" > "$OUT/greedy-route-same-request.json"

# ---------------------------------------------------------------------------
echo "== cleanup =="
aws apigateway delete-rest-api --rest-api-id "$API1"
sleep 5
aws apigateway delete-rest-api --rest-api-id "$API2"
aws lambda delete-function --function-name "$FUNCTION"

echo "saved to $OUT/"
