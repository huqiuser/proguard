/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2021 Guardsquare NV
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package proguard.gradle.plugin.android

import com.android.build.api.variant.ApplicationVariant
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import proguard.gradle.ProGuardTask
import proguard.gradle.plugin.android.AndroidPlugin.Companion.COLLECT_CONSUMER_RULES_TASK_NAME
import proguard.gradle.plugin.android.dsl.ProGuardAndroidExtension
import java.io.File

abstract class ProGuardTransform : DefaultTask() {

    @get:Internal
    abstract val variantProperty: Property<ApplicationVariant>

    @get:Internal
    abstract val proguardBlockProperty: Property<ProGuardAndroidExtension>

    @get:Internal
    abstract val bootClasspath: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val inputDirectories: ListProperty<Directory>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun transform() {

        val variant = variantProperty.get()
        val proguardBlock = proguardBlockProperty.get()

        val variantName: String = variant.name
        val variantBlock = proguardBlock.configurations.findVariantConfiguration(variantName)
            ?: throw RuntimeException("Invalid configuration: $variantName")

        val proguardTask = project.tasks.create(
            "proguardTask${variantName.capitalize()}",
            ProGuardTask::class.java
        )
        for (inputJar in inputJars.get()) {
            proguardTask.injars(inputJar.asFile)
        }
        for (inputDirectory in inputDirectories.get()) {
            proguardTask.injars(inputDirectory.asFile)
        }
        proguardTask.outjars(output.get().asFile)

        proguardTask.extraJar(File(output.get().asFile.parentFile, "extra.jar"))

        proguardTask.libraryjars(createLibraryJars(bootClasspath.get()))

        proguardTask.configuration(project.tasks.getByPath(COLLECT_CONSUMER_RULES_TASK_NAME + variantName.capitalize()).outputs.files)
        proguardTask.configuration(variantBlock.configurations.map { project.file(it.path) })

        val aaptRulesFile = getAaptRulesFile()
        if (File(aaptRulesFile).exists()) {
            proguardTask.configuration(aaptRulesFile)
        } else {
            project.logger.warn(
                "AAPT rules file not found: you may need to apply some extra keep rules for classes referenced from " + "resources in your own ProGuard configuration.",
            )
        }

        val mappingDir = project.buildDir.resolve("outputs/proguard/$variantName/mapping")
        if (!mappingDir.exists()) mappingDir.mkdirs()
        proguardTask.printmapping(File(mappingDir, "mapping.txt"))
        proguardTask.printseeds(File(mappingDir, "seeds.txt"))
        proguardTask.printusage(File(mappingDir, "usage.txt"))

        proguardTask.android()
        proguardTask.proguard()
    }

    private fun createLibraryJars(bootClasspath: List<RegularFile>): List<File> =
        bootClasspath.map { it.asFile }.toList()

    private fun getAaptRulesFile() = project.aaptRulesFile
}