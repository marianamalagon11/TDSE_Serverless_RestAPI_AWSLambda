# Screenshots

Console views of the two deployed architectures. They are here because the configuration of
architecture A is the part that cannot be read from the repository: it lives in API Gateway rather
than in the code.

| File | Shows |
|---|---|
| `both-apis.png` | the two REST APIs deployed side by side, `orders-api-custom` and `orders-api-proxy` |
| `architecture-a-resources.png` | architecture A: one explicitly declared method per operation, `GET` and `POST` on `/orders`, `GET` on `/orders/{id}` |
| `architecture-b-resources.png` | architecture B: the same two resources with a single `ANY` method each |

The two resource trees are the clearest visual summary of the comparison in section 9 of the main
README: the same contract, declared method by method on one side and delegated to the adapter on
the other.
