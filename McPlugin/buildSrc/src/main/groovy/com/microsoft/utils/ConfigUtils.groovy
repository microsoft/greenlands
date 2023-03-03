package com.microsoft.utils

import com.microsoft.Constants

import java.nio.file.Files
import java.nio.file.Path

class ConfigUtils {
    private static var _last_server_port = 33665
    private static var _last_server_rcon_port = 34775
    private static var _last_server_query_port = 35665
    private static var _last_proxy_query_port = 25564
    private static HashMap<String, String> _env_variables = null

    static getNextServerPort() {
        _last_server_port += 1
        return _last_server_port
    }

    static getNextRconPort() {
        _last_server_rcon_port += 1
        return _last_server_rcon_port
    }

    static getNextQueryPort() {
        _last_server_query_port += 1
        return _last_server_query_port
    }

    static getNextProxyQueryPort() {
        // TODO fix this. For some reason gradle is not always returning the expected port number (maybe a race condition?)
        // _last_proxy_query_port += 1
        //return _last_proxy_query_port

        // since we only have 1 proxy it's safe to always return the default MC port
        return 25565;
    }

    private static HashMap<String, String> readDotEnvFile(Path filePath) {
        var contents = Files.readString(filePath)
                .split("\n")
                .findAll { !(it.startsWith("#") || it.trim().isEmpty()) }

        var variableNameValueMap = new HashMap<String, String>();
        for (entry in contents) {
            def pos = entry.indexOf("=")
            def key = entry.substring(0, pos)
            def value = entry.substring(pos + 1).trim()

            variableNameValueMap.put(key, value)
        }

        return variableNameValueMap
    }

    /**
     * Get the value for a given variable name. When resolving the value of a variable it looks at
     * the .env files (.env and .env.local) as well as the enviroment variables of the process. The
     * following order of precedence is considered (from least to most):
     *
     * - .env file
     * - environment variables present in the process
     * - .env.local file
     */
    static String getEnvVariable(String varName) {
        if (_env_variables == null) {
            _env_variables = new HashMap<>();

            // first add all values from the .env, which contains the default values for
            // the application variables
            var dotEnvFile = Constants.PROJECT_ROOT_DIR.resolve(".env")
            _env_variables.putAll(readDotEnvFile(dotEnvFile))

            // we clone System.getEnv here since it returns an not-modifiable hashmap
            _env_variables.putAll(System.getenv())

            // then add whatever the .env.local file contains (if anything)
            var dotEnvLocal = Constants.PROJECT_ROOT_DIR.resolve(".env.local")
            if (dotEnvLocal.toFile().exists()) {
                _env_variables.putAll(readDotEnvFile(dotEnvLocal))
            }
        }

        return _env_variables.get(varName)
    }

    /**
     * Reads the dotenv files (`McPlugin/.env` and `McPlugin/.env.local`) and returns a hashmap
     * where the key is a key in the .env file, and the value is the value returned from
     * {@link #getEnvVariable(java.lang.String)}.
     *
     * When getting a value the following order of precedence is considered (from least to most):
     *
     * - .env file
     * - environment variables present in the process
     * - .env.local file
     */
    static HashMap<String, String> getDotEnvEntries() {
        // the .env file should always exist, so we can get the name of the variables from
        // there
        var dotEnvKeys = readDotEnvFile(Constants.PROJECT_ROOT_DIR.resolve(".env"))
                .keySet()

        var acc = new HashMap<String, String>();

        for (key in dotEnvKeys) {
            acc.put(key, getEnvVariable(key))
        }

        return acc;
    }
}
