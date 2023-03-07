﻿using Newtonsoft.Json;

namespace Greenlands.Api.Models;

public class ChallengeBaseInput
{
    [Required]
    public string TeamId { get; init; }

    [Required]
    public string TournamentId { get; init; }

    [Required]
    public string Name { get; init; }
}
