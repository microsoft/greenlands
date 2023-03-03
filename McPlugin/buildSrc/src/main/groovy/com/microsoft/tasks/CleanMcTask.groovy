package com.microsoft.tasks


import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import com.microsoft.Constants

class CleanMcTask extends DefaultTask {
    @TaskAction
    void cleanMinecraft() {
        Constants.PROJECT_ROOT_DIR = project.getRootDir().toPath()

        project.delete(Constants.PROJECT_ROOT_DIR.resolve("minecraft-server"))
    }
}
