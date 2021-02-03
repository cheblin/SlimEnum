package com.github.cheblin.slimenum.services

import com.github.cheblin.slimenum.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
