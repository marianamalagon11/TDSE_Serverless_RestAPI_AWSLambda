# Direct Lambda invocations: observed results

Function: `orders-create-order`, runtime `java17`, handler
`com.escuelaing.orders.lambda.CreateOrderHandler::handleRequest`, region `us-east-1`.

Every invocation was run with:

```bash
aws lambda invoke --function-name orders-create-order \
  --cli-binary-format raw-in-base64-out \
  --payload fileb://<file>.json \
  out.json
```

No API Gateway is involved here. The payload is the whole event, exactly as the handler sees it.

## 1. Valid input

`e1-valid.json`

```json
{"customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

```json
{"id":"ORD-100","customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

The JSON was bound to a `CreateOrderRequest`, the service assigned the id, and the returned `Order`
came back as JSON. No HTTP status code appears anywhere: this is a function invocation, not a
request.

## 2. Different values

`e2-other-values.json`

```json
{"customerId":"CUST-7","product":"Keyboard","quantity":2}
```

```json
{"id":"ORD-101","customerId":"CUST-7","product":"Keyboard","quantity":2}
```

## 3. Unknown fields are ignored

`e3-unknown-fields.json`

```json
{"customerId":"CUST-9","product":"Mouse","quantity":1,"discount":0.5,"notes":"a gift"}
```

```json
{"id":"ORD-102","customerId":"CUST-9","product":"Mouse","quantity":1}
```

`discount` and `notes` do not exist in `CreateOrderRequest` and caused no failure. This matches
`FAIL_ON_UNKNOWN_PROPERTIES` being disabled in the runtime serializer.

## 4. Property names are case sensitive

`e4-wrong-case.json`

```json
{"CustomerId":"CUST-9","product":"Mouse","quantity":1}
```

`FunctionError: Unhandled`

```json
{"errorMessage":"Field 'customerId' is required",
 "errorType":"com.escuelaing.orders.exception.InvalidOrderException"}
```

`CustomerId` did not bind to `customerId`, the field stayed `null` and the application rejected
the request. This matches `ACCEPT_CASE_INSENSITIVE_PROPERTIES` being disabled.

## 5. Scalar types are coerced

`e5-quantity-as-string.json`

```json
{"customerId":"CUST-9","product":"Mouse","quantity":"3"}
```

```json
{"id":"ORD-103","customerId":"CUST-9","product":"Mouse","quantity":3}
```

The string `"3"` became the `Integer` 3. Jackson applies its standard scalar coercion, so the type
in the JSON does not have to match the Java type exactly.

## 6. Missing fields arrive as null

`e6-missing-quantity.json`

```json
{"customerId":"CUST-9","product":"Mouse"}
```

`FunctionError: Unhandled`

```json
{"errorMessage":"Field 'quantity' is required",
 "errorType":"com.escuelaing.orders.exception.InvalidOrderException"}
```

`quantity` is declared as `Integer` and not as `int` precisely for this reason: a missing field
becomes `null` and can be reported as missing. Declared as `int` it would silently arrive as `0`,
because the runtime disables `FAIL_ON_NULL_FOR_PRIMITIVES`, and the error message would have been
the misleading "must be greater than zero".

## 7. A business rule violation becomes a function error

`e7-invalid-quantity.json`

```json
{"customerId":"CUST-9","product":"Mouse","quantity":0}
```

`FunctionError: Unhandled`

```json
{"errorMessage":"Field 'quantity' must be greater than zero",
 "errorType":"com.escuelaing.orders.exception.InvalidOrderException",
 "stackTrace":["com.escuelaing.orders.service.OrderService.validate(OrderService.java:88)",
               "com.escuelaing.orders.service.OrderService.createOrder(OrderService.java:40)",
               "com.escuelaing.orders.lambda.CreateOrderHandler.handleRequest(CreateOrderHandler.java:30)"]}
```

This is the shape of an unhandled exception in Lambda: the runtime catches it and answers with
`errorMessage`, `errorType` and a stack trace. Note what it is **not**: it is not a `400`, and the
internal stack trace is exposed. Turning this into the `400 Bad Request` with the contracted error
body is the job of the HTTP layer, and it is the reason section 12 introduces an adapter.

## 8. The Invoke API validates the JSON before the runtime does

`e8-lenient-json.json`

```
{customerId:'CUST-9',product:'Mouse',quantity:1}
```

```
InvalidRequestContentException: Could not parse request body into json:
Unexpected character ('c' (code 99)): was expecting double-quote to start field name
```

The runtime's `JsonFactory` is configured leniently (unquoted field names and single quotes are
allowed), so this experiment was expected to succeed. It did not: the **Lambda service itself**
rejects the payload before the function is ever invoked, and the function was not billed or run.
The lenient parser settings therefore do not apply to the payload of the Invoke API.

## 9. In-memory state is per execution environment, and it is lost

Three invocations in a row returned `ORD-100`, `ORD-101`, `ORD-102`, then `ORD-103` and `ORD-104`.
The static `OrderService` is created once per execution environment, so a warm container keeps
counting.

Then a configuration change was applied, which forces AWS to start a new execution environment:

```bash
aws lambda update-function-configuration --function-name orders-create-order \
  --environment "Variables={COLD_START_TEST=1}"
```

The very next invocation returned:

```json
{"id":"ORD-100","customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

The counter restarted and the four previously created orders disappeared. This is the practical
demonstration of the warning in the workshop statement: a Lambda execution environment is
ephemeral, so in-memory data is a cache at best, never storage. Two consequences follow for this
API: ids are only unique within one execution environment, and an order created by one request may
be invisible to the next request if it lands on a different container.
