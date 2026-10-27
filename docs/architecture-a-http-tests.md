# Architecture A: requests and responses observed

API Gateway custom (non-proxy) integration, three Lambda functions.

Base URL of the deployment used for these tests:

```
https://gbsldccum1.execute-api.us-east-1.amazonaws.com/dev
```

The identifier changes on every redeployment, so the commands below use `$BASE`:

```bash
BASE=https://<api-id>.execute-api.us-east-1.amazonaws.com/dev
```

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

The body is the error document of the contract, not the raw Lambda error: the integration response
for `404` applies the template `$input.path('$.errorMessage')`, which replaces
`{"errorMessage": ..., "errorType": ..., "stackTrace": [...]}` with the document the function put
inside `errorMessage`. The stack trace never reaches the client.

## Search by customer, 200 OK

```bash
curl -i "$BASE/orders?customerId=CUST-42"
```

```
HTTP/1.1 200 OK

[{"id":"ORD-001","customerId":"CUST-42","product":"MacBook Air","quantity":1},
 {"id":"ORD-002","customerId":"CUST-42","product":"USB-C Adapter","quantity":3}]
```

## Search with no matches, 200 OK and an empty array

```bash
curl -i "$BASE/orders?customerId=CUST-100"
```

```
HTTP/1.1 200 OK

[]
```

Not a `404`. The collection `/orders` exists and the filter simply matched nothing, so the correct
answer is an empty representation of it.

## Missing query parameter, 400 Bad Request

```bash
curl -i "$BASE/orders"
```

```
HTTP/1.1 400 Bad Request

{"error":"INVALID_ORDER","message":"Query parameter 'customerId' is required"}
```

The mapping template produced `{"customerId": ""}` and the application rejected it. The validation
happens in Java, not in API Gateway, even though `method.request.querystring.customerId` is
declared as required: that declaration is only documentation unless a request validator is attached
to the method.

## Successful creation, 201 Created

```bash
curl -i -X POST $BASE/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}'
```

```
HTTP/1.1 201 Created

{"id":"ORD-100","customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

`201` is configured on the integration response of the method. The Java code has no idea that this
operation answers `201` while the others answer `200`.

## Invalid input, 400 Bad Request

```bash
curl -i -X POST $BASE/orders -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":0}'
```

```
HTTP/1.1 400 Bad Request

{"error":"INVALID_ORDER","message":"Field 'quantity' must be greater than zero"}
```

```bash
curl -i -X POST $BASE/orders -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-42","quantity":1}'
```

```
HTTP/1.1 400 Bad Request

{"error":"INVALID_ORDER","message":"Field 'product' is required"}
```

## Wrong content type, 415 Unsupported Media Type

```bash
curl -i -X POST $BASE/orders -d 'customerId=CUST-42'
```

```
HTTP/1.1 415 Unsupported Media Type

{"message": "Unsupported Media Type"}
```

API Gateway answers this one on its own, because the integration is configured with
`passthrough-behavior NEVER` and a template exists only for `application/json`. Note that the body
does **not** follow the error format of the contract: API Gateway generates it, and matching the
contract would require configuring a gateway response.

## A method that does not exist, 403

```bash
curl -i -X DELETE $BASE/orders/ORD-001
```

```
HTTP/1.1 403 Forbidden

{"message":"Missing Authentication Token"}
```

`DELETE` is not implemented yet (section 17). The answer is worth recording because it is
misleading: API Gateway returns `403 Missing Authentication Token` for any route or method it does
not know, which has nothing to do with authentication and would ideally be a `404` or a `405`.

## State is not shared between functions

The `POST` above created `ORD-100`. Asking for it immediately afterwards:

```bash
curl -i $BASE/orders/ORD-100
```

```
HTTP/1.1 404 Not Found

{"error":"ORDER_NOT_FOUND","message":"Order ORD-100 does not exist"}
```

```bash
curl -i "$BASE/orders?customerId=CUST-42"
```

```
HTTP/1.1 200 OK

[{"id":"ORD-001",...},{"id":"ORD-002",...}]
```

The new order is nowhere to be found. Architecture A uses one Lambda function per operation, so
`orders-create-order` stored it in the memory of its own execution environment and
`orders-get-order` runs in a different one. This is not a bug in the HTTP design, it is the
consequence of combining an in-memory repository with several independent functions, and it is the
clearest practical argument for a shared store.
