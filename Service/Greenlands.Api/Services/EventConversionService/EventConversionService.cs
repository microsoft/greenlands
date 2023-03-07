using Azure.Messaging.EventHubs;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Swashbuckle.AspNetCore.Annotations;
using System.Reflection;

namespace Greenlands.Api.Services;

public class EventConversionService : IEventConversionService
{
    private readonly string discriminatorFieldName;
    private readonly IDictionary<string, Type> eventTypeStringToTypeValueMap;

    /// <summary>
    /// While creating an instance of this service, the local <see cref="eventTypeStringToTypeValueMap"/>
    /// is populated via reflection. This map is used in the other methods of this service to avoid reflection
    /// access every time and improve performance.
    /// </summary>
    /// <exception cref="Exception"></exception>
    public EventConversionService()
    {
        eventTypeStringToTypeValueMap = new Dictionary<string, Type>();

        var baseEventType = typeof(BaseEvent);

        // extract and set the name of the field we'll be using as the discriminator
        var discriminatorAttribute = baseEventType.GetCustomAttribute<SwaggerDiscriminatorAttribute>();
        if (discriminatorAttribute == null)
        {
            throw new Exception($"'{baseEventType.Name}' doesn't have a {nameof(SwaggerDiscriminatorAttribute)} attribute! " +
                $"Cannot associate discriminator values with event implementation types");
        }

        discriminatorFieldName = discriminatorAttribute.PropertyName;

        // Populate discriminator -> type map for every event type that is a subclass of baseEventType
        var assm = Assembly.GetExecutingAssembly();
        var derivedEvents = assm.GetTypes()
            .Where(p => p.Namespace != null &&
                        p.Namespace == baseEventType.Namespace &&
                        p.IsSubclassOf(baseEventType))
            .ToList();


        foreach (var subClass in derivedEvents)
        {
            var className = subClass.Name;
            eventTypeStringToTypeValueMap.Add(className, subClass);
        }
    }

    /// <summary>
    /// Gets the Type that is registered for the discriminator value in the provided JObject
    /// </summary>
    private Type GetEventTypeFromJObject(JObject joEvent)
    {
        var discriminatorValue = joEvent.GetValue(discriminatorFieldName, StringComparison.OrdinalIgnoreCase).ToString();

        if (!eventTypeStringToTypeValueMap.ContainsKey(discriminatorValue))
        {
            throw new KeyNotFoundException($"The discriminator value in the provided serialized event is not known to us '{discriminatorValue}'");
        }

        var mappedType = eventTypeStringToTypeValueMap[discriminatorValue];
        return mappedType;
    }

    public BaseEvent DeserializeEvent(EventData eventData)
    {
        if (!eventData.Properties.TryGetValue(discriminatorFieldName, out var discriminatorValueObj))
        {
            throw new ArgumentException($"The properties of the provided {nameof(EventData)} do not contain an entry for the discrimator key: {discriminatorFieldName}");
        }

        var discriminatorValue = (string)discriminatorValueObj;

        if (!eventTypeStringToTypeValueMap.ContainsKey(discriminatorValue))
        {
            throw new KeyNotFoundException($"The discriminator value in the provided serialized event is not known to us '{discriminatorValue}'");
        }

        var mappedType = eventTypeStringToTypeValueMap[discriminatorValue];

        var eventBody = eventData.EventBody.ToString();

        return (BaseEvent)JsonConvert.DeserializeObject(eventBody, mappedType);
    }

    public BaseEvent DeserializeEvent(JObject joEvent)
    {
        var discriminatorValue = joEvent.GetValue(discriminatorFieldName, StringComparison.OrdinalIgnoreCase);

        if (discriminatorValue == null)
        {
            throw new ArgumentException($"The provided serialized event does not have a value for the expected discriminator field '{discriminatorFieldName}'");
        }

        var targetType = GetEventTypeFromJObject(joEvent);

        return (BaseEvent)joEvent.ToObject(targetType);
    }
}
