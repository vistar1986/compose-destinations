@file:Suppress("ObjectPropertyName")

package com.ramcosta.composedestinations.codegen

import com.ramcosta.composedestinations.codegen.commons.firstTypeInfoArg
import com.ramcosta.composedestinations.codegen.commons.isCustomArrayOrArrayListTypeNavArg
import com.ramcosta.composedestinations.codegen.commons.sanitizePackageName
import com.ramcosta.composedestinations.codegen.facades.CodeOutputStreamMaker
import com.ramcosta.composedestinations.codegen.model.CodeGenConfig
import com.ramcosta.composedestinations.codegen.model.CodeGenProcessedDestination
import com.ramcosta.composedestinations.codegen.model.NavTypeSerializer
import com.ramcosta.composedestinations.codegen.model.RawDestinationGenParams
import com.ramcosta.composedestinations.codegen.model.RawNavGraphGenParams
import com.ramcosta.composedestinations.codegen.model.SubModuleInfo
import com.ramcosta.composedestinations.codegen.servicelocator.ServiceLocator
import com.ramcosta.composedestinations.codegen.servicelocator.customNavTypeWriter
import com.ramcosta.composedestinations.codegen.servicelocator.defaultKtxSerializableNavTypeSerializerWriter
import com.ramcosta.composedestinations.codegen.servicelocator.destinationWithNavArgsMapper
import com.ramcosta.composedestinations.codegen.servicelocator.destinationsWriter
import com.ramcosta.composedestinations.codegen.servicelocator.initialValidator
import com.ramcosta.composedestinations.codegen.servicelocator.moduleOutputWriter
import com.ramcosta.composedestinations.codegen.writers.ModuleRegistryWriter
import java.util.Locale

internal const val DEFAULT_GEN_PACKAGE_NAME = "com.ramcosta.composedestinations.generated"
internal lateinit var codeGenBasePackageName: String
internal lateinit var moduleName: String

class CodeGenerator(
    override val codeGenerator: CodeOutputStreamMaker,
    override val isBottomSheetDependencyPresent: Boolean,
    override val codeGenConfig: CodeGenConfig
) : ServiceLocator {

    fun generate(
        destinations: List<RawDestinationGenParams>,
        navGraphs: List<RawNavGraphGenParams>,
        navTypeSerializers: List<NavTypeSerializer>,
        submodules: List<SubModuleInfo>
    ) {
        initialValidator.validate(
            navGraphs = navGraphs,
            destinations = destinations,
            submoduleResultSenders = submodules
                .flatMap { it.publicResultSenders }
                .associateBy { it.genDestinationQualifiedName }
        )

        initConfigurationValues()

        val processedDestinations: List<CodeGenProcessedDestination> = destinationWithNavArgsMapper.map(destinations)

        val navTypeNamesByType = customNavTypeWriter.write(processedDestinations, navTypeSerializers)

        moduleOutputWriter(navTypeNamesByType, submodules).write(navGraphs, processedDestinations)

        destinationsWriter(navTypeNamesByType).write(processedDestinations)

        if (shouldWriteKtxSerializableNavTypeSerializer(processedDestinations)) {
            defaultKtxSerializableNavTypeSerializerWriter.write()
        }
    }

    private fun initConfigurationValues() {
        moduleName = codeGenConfig.moduleName?.replaceFirstChar { it.uppercase(Locale.US) } ?: ""
        val defaultPackageName = if (moduleName.isEmpty()) {
            DEFAULT_GEN_PACKAGE_NAME
        } else {
            "$DEFAULT_GEN_PACKAGE_NAME.${moduleName.lowercase()}".sanitizePackageName()
        }
        codeGenBasePackageName = codeGenConfig.packageName?.sanitizePackageName() ?: defaultPackageName
    }

    private fun shouldWriteKtxSerializableNavTypeSerializer(
        destinations: List<CodeGenProcessedDestination>,
    ) = destinations.any {
        it.navArgs.any { navArg ->
            if (navArg.type.isCustomArrayOrArrayListTypeNavArg()) {
               navArg.type.value.firstTypeInfoArg.run {
                   isKtxSerializable &&
                           !hasCustomTypeSerializer &&
                           !isParcelable &&
                           !isSerializable
               }
            } else {
                navArg.type.run {
                    isKtxSerializable &&
                            !hasCustomTypeSerializer &&
                            !isParcelable &&
                            !isSerializable
                }
            }
        }
    }

    companion object {
        fun generateModuleRegistryPathInfo(
            codeGenerator: CodeOutputStreamMaker,
            moduleRegistryPath: String,
            moduleRegistryId: String
        ) {
            ModuleRegistryWriter.generateModuleRegistryPathInfo(codeGenerator, moduleRegistryPath, moduleRegistryId)
        }
    }
}
