package com.escuelaing.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A diagnostic function that answers with the event it received.
 *
 * <p>It exists to answer one question with evidence instead of documentation: what exactly does
 * API Gateway send to a Lambda in a proxy integration? Behind an {@code ANY /{proxy+}} route it
 * returns the raw event as the response body, so any request can be turned into a saved example.
 * The captured events live in {@code docs/proxy-events/}.
 *
 * <p>It implements {@link RequestStreamHandler} rather than {@code RequestHandler} precisely
 * because that interface bypasses the automatic JSON mapping: the bytes are read exactly as the
 * runtime received them, with nothing dropped by a POJO that does not declare every field.
 *
 * <p>This class is not part of the API. It is a tool, and it is documented as such.
 */
public class EventEchoHandler implements RequestStreamHandler {

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
            throws IOException {
        String event = new String(input.readAllBytes(), StandardCharsets.UTF_8);

        String response = "{\"statusCode\":200,"
                + "\"headers\":{\"Content-Type\":\"application/json\"},"
                + "\"body\":\"" + JsonText.escape(event) + "\"}";

        output.write(response.getBytes(StandardCharsets.UTF_8));
    }
}
