# PlaiGround Client Generation

The Service exposes a RESTful API that is intended to be used by several consumers: the Dashboard, the Minecraft Plugin and the Agent Toolkit.

The service generates a Swagger.json which describes the API according to the OpenAPI specification.
Then we use Open API generator tool to generate a client library in the desired languages.

- The Minecraft plugin uses Java
- The Dashboard uses TypeScript
- The AgentToolkit uses Python
