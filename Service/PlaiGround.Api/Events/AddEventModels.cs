using Microsoft.OpenApi.Models;
using Swashbuckle.AspNetCore.SwaggerGen;
using System.Reflection;

namespace PlaiGround.Api.Events;

/// <summary>
/// This Document Filter ensures that all Event models are added to Swagger. The added events are those that
/// inherit from <typeparamref name="BaseEventType"/> and that are found it's same namespace.
/// </summary>
public class AddEventModels<BaseEventType> : IDocumentFilter
{
    /// <summary>
    /// All our models are subclasses of <typeparamref cref="BaseEventType"/>, which itself contains all properties
    /// that should be common among all out models in a given version. This method returns a list of all the events 
    /// that inherit from it <typeparamref cref="BaseEventType"/> and are under the same namespace.
    /// </summary>
    public IEnumerable<Type> getEventBaseTypeAndEventTypes()
    {
        var baseEventType = typeof(BaseEventType);

        var assm = Assembly.GetExecutingAssembly();
        var derivedEvents = assm.GetTypes()
            .Where(p => p.Namespace != null &&
                        p.Namespace == baseEventType.Namespace &&
                        p.IsSubclassOf(baseEventType))
            .ToList();

        return derivedEvents;
    }

    /// <summary>
    /// Adds all EventHub event models as swagger schemas. See <see cref="getEventBaseTypeAndEventTypes"/>
    /// for how these models are obtained.
    /// </summary>
    public void Apply(OpenApiDocument openapiDoc, DocumentFilterContext context)
    {
        var baseEventType = typeof(BaseEventType);
        var derivedEvents = getEventBaseTypeAndEventTypes();

        context.SchemaGenerator.GenerateSchema(baseEventType, context.SchemaRepository);

        foreach (var eventType in derivedEvents)
        {
            context.SchemaGenerator.GenerateSchema(eventType, context.SchemaRepository);
        }
    }
}
