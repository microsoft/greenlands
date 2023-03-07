using Azure.Messaging.EventHubs;
using Newtonsoft.Json.Linq;

namespace Greenlands.Api.Services;

public interface IEventConversionService
{
    /// <summary>
    /// Returns an initialized instance of the type the provided event data corresponds to.
    /// </summary>
    /// <param name="eventData"></param>
    /// <returns></returns>
    /// <exception cref="ArgumentException">
    /// If the provided event data an entry for the discriminator field among its properties
    /// </exception>
    /// <exception cref="KeyNotFoundException">
    /// If the provided serialized event has a value for the discriminator field that we don't know about
    /// </exception>
    public BaseEvent DeserializeEvent(EventData eventData);

    /// <summary>
    /// Returns an initialized instance of the type the provided JObject instance this event corresponds to.
    /// </summary>
    /// <param name="serializedEvent"></param>
    /// <returns></returns>
    /// <exception cref="ArgumentException">
    /// If the provided JObject event does not have a value for the discriminator field
    /// </exception>
    /// <exception cref="KeyNotFoundException">
    /// If the provided JObject event has a value for the discriminator field that we don't know about
    /// </exception>
    BaseEvent DeserializeEvent(JObject joEvent);

}
