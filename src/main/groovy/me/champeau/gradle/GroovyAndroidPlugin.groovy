package me.champeau.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile

/**
 * This is the main plugin file. Put a description of your plugin here.
 */
class GroovyAndroidPlugin implements Plugin<Project> {

    private static String LATEST_SUPPORTED="0.12.0"

    private static List RUNTIMEJARS_COMPAT = [
            { it.runtimeJars },
            { it.bootClasspath }
    ]

    void apply(Project project) {

        def plugin = project.plugins.findPlugin('android')?:project.plugins.findPlugin('android-library')
        if (!plugin) {
            throw new GradleException('You must apply the Android plugin or the Android library plugin before using the groovy-android plugin')
        }

        def groovyPlugin = this
        project.android {

            packagingOptions {
                // workaround for http://stackoverflow.com/questions/20673625/android-gradle-plugin-0-7-0-duplicate-files-during-packaging-of-apk
                exclude 'META-INF/LICENSE.txt'
                exclude 'META-INF/groovy-release-info.properties'
            }

            // Forces Android Studio to recognize groovy folder as code
            sourceSets {
              main.java.srcDir('src/main/groovy')
              androidTest.java.srcDir('src/androidTest/groovy')
            }

            def variants = plugin.class.name.endsWith('.LibraryPlugin')?libraryVariants:applicationVariants

            variants.all {

                project.task("groovy${name}Compile",type: GroovyCompile) {
                    source = javaCompile.source + project.fileTree('src/main/java').include('**/*.groovy') +
                            project.fileTree('src/main/groovy').include('**/*.groovy')
                    destinationDir = javaCompile.destinationDir
                    classpath = javaCompile.classpath
                    groovyClasspath = classpath
                    sourceCompatibility = '1.6'
                    targetCompatibility = '1.6'
                    doFirst {
                        def runtimeJars = groovyPlugin.getRuntimeJars(project, plugin)
                        classpath = project.files(runtimeJars) + classpath
                    }
                }
                javaCompile.dependsOn("groovy${name}Compile")
                javaCompile.enabled = false

                project.task("groovy${name}TestCompile", type: GroovyCompile, dependsOn: "groovy${name}Compile") {
                    source = project.fileTree('src/androidTest/java').include('**/*.groovy') +
                            project.fileTree('src/androidTest/groovy').include('**/*.groovy')
                    destinationDir = project.file("$project.buildDir/intermediates/classes/test/$dirName")
                    classpath = javaCompile.classpath + project.files("$project.buildDir/intermediates/classes/$dirName")
                    groovyClasspath = classpath
                    sourceCompatibility = '1.6'
                    targetCompatibility = '1.6'
                    doFirst {
                        def runtimeJars = groovyPlugin.getRuntimeJars(project, plugin)
                        classpath = project.files(runtimeJars) + classpath
                    }
                }
                def javaTestTask = project.tasks.findByName("compile${name?.capitalize()}TestJava")
                javaTestTask?.dependsOn("groovy${name}TestCompile")
            }
        }
        project.logger.info("Detected Android plugin version ${getAndroidPluginVersion(project)}")

    }

    private String getAndroidPluginVersion(Project project) {

        def dependency = [project,project.rootProject].collect {
            it.buildscript.configurations.classpath.resolvedConfiguration.firstLevelModuleDependencies.find {
                it.moduleGroup == 'com.android.tools.build' && it.moduleName == 'gradle'
            }
        }.find()

        if (dependency) {
            return dependency.moduleVersion
        }
        project.logger.warn("Unable to determine Android build tools version from classpath. Falling back to default ($LATEST_SUPPORTED).")

        LATEST_SUPPORTED
    }

    def getRuntimeJars(Project project, plugin) {
        int index
        switch (getAndroidPluginVersion(project)) {
            case ~/0\.9\..*/:
                index = 0
                break
            case ~/0\.10\..*/:
                index = 1
                break
            case ~/0\.11\..*/:
                index = 1
                break
            case ~/0\.12\..*/:
                index = 1
                break
            default:
                index = RUNTIMEJARS_COMPAT.size()-1
        }
        def fun = RUNTIMEJARS_COMPAT[index]
        fun(plugin)
    }
}
