# Authorization

Authorization is the process of verifying if the user can perform the operation specified by request.


## Getting and identifier for the current user

All the information about the user comes from the token on the Authorization header of the request.

Tokens issued to access Greenlands Service look similar to this (note, the values below are just examples):


```json
{
  "aud": "abc5a5a-ca70-4665-9f0d-5619fabcfa44",
  "iss": "https://login.microsoftonline.com/72f981bf-86f1-41af-91ab-6d7cd01abc47/v2.0",
  "iat": 1652459307,
  "nbf": 1652459307,
  "exp": 1652464398,
  "aio": "something something",
  "azp": "abc5a5a-ca70-4665-9f0d-5619ff95fa44",
  "azpacr": "0",
  "name": "John Doe",
  "oid": "abc5a5a-ca70-4665-9f0d-5619fabcfa44",
  "preferred_username": "john@email.com",
  "rh": "0.AQEAv4j5creGr0GRq23180BHbR1p4rb9wymVGnw1WGf-V-kQaAPg.",
  "scp": "Greenlands.ReadWrite",
  "sub": "iJc4A_epT5Pf3IH8DaGD-V_YL3gSUf8eqV9pQ1XAwYs",
  "tid": "abc5a5a-ca70-4665-9f0d-5619fabcfa44",
  "uti": "bESZT1aEc0-2ix4MIRfhAA",
  "ver": "2.0"
}
```

Dotnet `AddMicrosoftIdentityWebApi` middleware will attempt to apply claims from tokens to the HttpContext.User object.

For more information about each claim see:

https://docs.microsoft.com/en-us/azure/active-directory/develop/id-tokens#payload-claims

The two important claims for user identity are "oid" and "sub".


## "oid" Example (The one we use)

```json
"oid": "abc5a5a-ca70-4665-9f0d-5619fabcfa44",
```

This ID uniquely identifies the user across applications - two different applications signing in the same user will receive the same value in the oid claim.

This is commonly retrieved using: `var objectId = User.GetObjectId();` but may also be retrieved using `var securityId = User.FindFirst(ClaimTypes.Sid);`


## "sub" Example

```json
"sub": "iJc4A_epT5Pf3IH8DaGD-V_YL3gSUf8eqV9pQ1XAwYs"
```

Subject is unique to a particular application ID. If a single user signs into two different apps using two different client IDs, those apps will receive two different values for the subject claim.

This can be retrieved using: `var userId = User.FindFirst(ClaimTypes.NameIdentifier);`


## Resource Authorization

Add checks to any operation would modify or delete a resource to verify one of two things.

**1. Is the current user a member of a given team?**
   This is used when resources are being created. If user A tries who is member of team 1, attempts to create resource for team 2, it should be denies. Only team members can add resources to their teams.

**2. Is the current user a member of team which owns a given tournament?**
   This is used when modifying or deleting a resource. We check the tournament id the resource belongs to and see if the user is on a team that owns that tournament.
  If yes, they have the capability to manage that resource.


### Caching

In order to perform these authorization checks, we need to build a mapping between the current user and the ids of teams they are a member of. From those teams get the tournaments. We have 2 mappings for each user like this

userId1:teams -> [teamId1, teamId2]
userId1:tournaments -> [tournamentId1, tournamentId2, tournamentId3, ...]

We use Redix and a "read-through" caching strategy. We first check Redis for the data, if data does NOT exist, we get it from database and write to cache with expiration.


## Cache Invalidation (Outstanding Issue ⚠️)

If in cache we have `{ "userId1:teams": ['team1'] }`, but then the user is added to another team2. They should not have to wait for the cache to expire for us them to perform operations on team2.

