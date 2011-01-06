package org.jfrog.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.text.SimpleDateFormat

class Status {
    String status
    Boolean release = null

    def Status(Project project) {
        this(project, null)
    }

    def Status(Project project, List<String> subProjects) {
        this.release = Boolean.valueOf(project.getProperty("${project.name}-release"))
        if (release) {
          status = 'release'
        } else {
          status = 'integration'
        }
    }

    String toString() {
        status
    }
}
