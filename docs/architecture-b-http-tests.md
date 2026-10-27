# Architecture B: requests and responses observed

API Gateway Lambda proxy integration, one function, `orders-api-proxy`.

```
https://oyu0y3kva7.execute-api.us-east-1.amazonaws.com/dev
```

```bash
BASE=https://<api-id>.execute-api.us-east-1.amazonaws.com/dev
```

Same contract as architecture A. What changed is where each decision is written: every status
code, header and error body below comes from
[`OrdersApiProxyHandler`](../src/main/java/com/escuelaing/orders/lambda/proxy/OrdersApiProxyHandler.java)
and from [`HttpResponses`](../src/main/java/com/escuelaing/orders/lambda/proxy/HttpResponses.java),
not from API Gateway configuration.

## Successful retrieval, 200 OK

```bash
curl -i $BASE/orders/ORD-001
```

```
HTTP/1.1 200 OK
Content-Type: application/json

{"id":"ORD-001","customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

## Missing order, 404 Not Found

```bash
curl -i $BASE/orders/ORD-999
```

```
HTTP/1.1 404 Not Found

{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}
```

## Search by customer, 200 OK

```bash
curl -i "$BASE/orders?customerId=CUST-42"
```

```
HTTP/1.1 200 OK

[{"id":"ORD-001","customerId":"CUST-42","product":"MacBook Air","quantity":1},
 {"id":"ORD-002","customerId":"CUST-42","product":"USB-C Adapter","quantity":3}]
```

## No matches, 200 OK with an empty array

```bash
curl -i "$BASE/orders?customerId=CUST-100"
```

```
HTTP/1.1 200 OK

[]
```

## Missing query parameter, 400 Bad Request

```bash
curl -i "$BASE/orders"
```

```
HTTP/1.1 400 Bad Request

{"error":"INVALID_ORDER","message":"Query parameter 'customerId' is required"}
```

The adapter read `queryStringParameters`, which API Gateway sends as `null` rather than as an
empty map, and passed `null` to the application, which rejected it.

## Successful creation, 201 Created with a Location header

```bash
curl -i -X POST $BASE/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-7","product":"Monitor","quantity":2}'
```

```
HTTP/1.1 201 Created
Content-Type: application/json
Location: /dev/orders/ORD-100

{"id":"ORD-100","customerId":"CUST-7","product":"Monitor","quantity":2}
```

The `Location` header is the clearest difference from architecture A, where it could not be
produced at all: a response parameter mapping can copy a field of the body into a header, but it
cannot build `/dev/orders/` + `ORD-100`. Here the adapter builds it from the path of the request
itself, so it keeps working under any stage or domain.

## Invalid input, 400 Bad Request

```bash
curl -i -X POST $BASE/orders -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":0}'
```

```
HTTP/1.1 400 Bad Request

{"error":"INVALID_ORDER","message":"Field 'quantity' must be greater than zero"}
```

## A body that is not JSON, 400 Bad Request

```bash
curl -i -X POST $BASE/orders -H "Content-Type: application/json" -d '{no es json'
```

```
HTTP/1.1 400 Bad Request

{"error":"MALFORMED_JSON","message":"The request body is not valid JSON"}
```

API Gateway forwards the body as an unparsed string and never inspects it, so this case only
exists in architecture B, and the adapter has to handle it explicitly.

## Wrong media type, 415 Unsupported Media Type

```bash
curl -i -X POST $BASE/orders -d 'customerId=CUST-42'
```

```
HTTP/1.1 415 Unsupported Media Type

{"error":"UNSUPPORTED_MEDIA_TYPE","message":"The request body must be application/json"}
```

Architecture A answered `415` for free, through `passthrough-behavior NEVER`, but with a body of
its own invention. Here the check is eight lines of Java, and in exchange the answer follows the
error format of the contract like every other error.

## A method with no operation, 405 Method Not Allowed

```bash
curl -i -X DELETE $BASE/orders/ORD-001
```

```
HTTP/1.1 405 Method Not Allowed

{"error":"METHOD_NOT_ALLOWED","message":"DELETE is not supported on /orders/{id}"}
```

Architecture A answered `403 Missing Authentication Token` to the same request, because the method
was simply not declared. The `ANY` method of architecture B sends everything to the function, which
can give a correct and explainable answer. `DELETE` is implemented in section 11.

## Deleting an order, 204 No Content

```bash
curl -i -X DELETE $BASE/orders/ORD-001
```

```
HTTP/1.1 204 No Content
```

No body, zero bytes. The response carries no `Content-Type` either, since there is nothing to
describe.

```bash
curl -i $BASE/orders/ORD-001
```

```
HTTP/1.1 404 Not Found

{"error":"ORDER_NOT_FOUND","message":"Order ORD-001 does not exist"}
```

```bash
curl -i "$BASE/orders?customerId=CUST-42"
```

```
HTTP/1.1 200 OK

[{"id":"ORD-002","customerId":"CUST-42","product":"USB-C Adapter","quantity":3}]
```

The deleted order is gone from the search as well, since both read it from the same repository.

## Deleting an order that does not exist, 404 Not Found

```bash
curl -i -X DELETE $BASE/orders/ORD-999
```

```
HTTP/1.1 404 Not Found

{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}
```

Deleting twice gives the same answer: the second attempt finds nothing to delete and is reported
exactly like any other missing order.

**No API Gateway change was needed for any of this.** The resource already had an `ANY` method, so
the only thing that changed was the code of the function.
