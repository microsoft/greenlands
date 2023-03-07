#See https://aka.ms/containerfastmode to understand how Visual Studio uses this Dockerfile to build your images for faster debugging.

# Must use base images from MS approved list: https://eng.ms/docs/more/containers-secure-supply-chain/approved-images
FROM mcr.microsoft.com/dotnet/aspnet:6.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build
WORKDIR /src
COPY ["Greenlands.Api/Greenlands.Api.csproj", "Greenlands.Api/"]
RUN dotnet restore "Greenlands.Api/Greenlands.Api.csproj"
COPY . .
WORKDIR "/src/Greenlands.Api"
RUN dotnet build "Greenlands.Api.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "Greenlands.Api.csproj" -c Release -o /app/publish

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "Greenlands.Api.dll"]