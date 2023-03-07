using Microsoft.OpenApi.Any;
using Microsoft.OpenApi.Models;
using Swashbuckle.AspNetCore.SwaggerGen;

namespace Greenlands.Api.Events;

/// <summary>
/// This SchemaFilter adds extra properties to the models when generating the swagger.json. These properties
/// are added as "extensions" of the models. We then use these extensions in our custom templates for the
/// openapi generator, to conditionally generate different functionality for the models.
/// </summary>
public class ExtendEventSchemas<BaseEventType> : ISchemaFilter
{
    public void Apply(OpenApiSchema schema, SchemaFilterContext context)
    {
        // If the current schema is the BaseEventType type for the events we want to add then mark
        // this schema as abstract, which will cause the generator to create an abstract class for it
        if (context.Type == typeof(BaseEventType))
        {
            schema.Extensions.Add("x-is-base-event-class", new OpenApiBoolean(true));
        }

        // If the current schema is subclass of the BaseEventType class then add an extension informing
        // the generator what the name of the parent is. This will cause the generator to create this as
        // a subclass of BaseEventType.
        if (context.Type.IsSubclassOf(typeof(BaseEventType)))
        {
            schema.Extensions.Add("x-parent-event-class-name", new OpenApiString(typeof(BaseEventType).Name));
        }
    }
}
