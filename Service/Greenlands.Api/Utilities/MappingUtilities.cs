using System.Reflection;

namespace Greenlands.Api.Utilities;

public class MappingUtilities
{
    public static void MapPropertiesFromSourceToDestination<SourceType, DestinationType>(SourceType sourceInstance, DestinationType destinationInstance)
    {
        var t = typeof(SourceType);
        foreach (var property in t.GetProperties(BindingFlags.Public | BindingFlags.Instance))
        {
            if (property.CanWrite && property.CanRead)
            {
                property.SetValue(destinationInstance, property.GetValue(sourceInstance));
            }
        }
    }
}
