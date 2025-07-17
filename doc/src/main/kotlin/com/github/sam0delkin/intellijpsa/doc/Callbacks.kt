@file:Suppress("ktlint:standard:max-line-length")

package com.github.sam0delkin.intellijpsa.doc

import com.github.sam0delkin.intellijpsa.language.php.model.PhpInfoModel
import com.github.sam0delkin.intellijpsa.model.InfoModel
import com.github.sam0delkin.intellijpsa.model.StaticCompletionsModel
import com.github.sam0delkin.intellijpsa.model.action.EditorActionInputModel
import com.github.sam0delkin.intellijpsa.model.completion.CompletionModel
import com.github.sam0delkin.intellijpsa.model.psi.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.template.GenerateFileFromTemplateData
import com.github.sam0delkin.intellijpsa.model.template.TemplateDataModel
import com.github.sam0delkin.intellijpsa.model.typeProvider.TypeProvidersModel
import com.github.sam0delkin.intellijpsa.util.BuildConfig
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.QueryParam

@OpenAPIDefinition(
    info =
        Info(
            title = "PSA",
            description = "Project Specific Autocomplete documentation",
            version = BuildConfig.PLUGIN_VERSION,
            license = License(name = "MIT", url = "https://github.com/sam0delkin/intellij-psa?tab=MIT-1-ov-file#readme"),
            contact = Contact(url = "https://github.com/sam0delkin/intellij-psa", name = "sam0delkin", email = "me@s-l.dev"),
        ),
)
@Path("/")
class Callbacks {
    @Operation(
        tags = ["Methods"],
        description =
            "Get Info from your PSA script. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa?tab=readme-ov-file#custom-autocomplete-info\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "info",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Info Model",
                content = [Content(mediaType = "application/json", schema = Schema(oneOf = [InfoModel::class, PhpInfoModel::class]))],
            ),
        ],
    )
    @POST
    @Path("Info")
    fun info(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["Info"], defaultValue = "Info"),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"], defaultValue = "0"),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
    ): InfoModel? = null

    @Operation(
        tags = ["Methods"],
        description =
            "Get Completions for the given PSI element. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa?tab=readme-ov-file#completions--goto\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "getCompletions",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Completions Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = CompletionModel::class))],
            ),
        ],
    )
    @POST
    @Path("GetCompletions")
    fun getCompletions(
        @Parameter(
            name = "PSA_TYPE",
            description = "Type of the request",
            schema =
                Schema(
                    types = ["string"],
                    allowableValues = [
                        "Completion",
                        "BatchCompletion",
                        "GoTo",
                        "BatchGoTo",
                    ],
                    defaultValue = "Completion",
                ),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            name = "PSA_DEBUG",
            description = "Is debug enabled or not",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"], defaultValue = "0"),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
        @Parameter(
            name = "PSA_LANGUAGE",
            schema = Schema(types = ["string"]),
        )
        @QueryParam("PSA_LANGUAGE") psaLanguage: String,
        @Parameter(
            name = "PSA_OFFSET",
            schema = Schema(types = ["string"]),
        )
        @QueryParam("PSA_OFFSET") psaOffset: String,
        @RequestBody(
            description = "Note that body will be passed as file path to the JSON content via `PSA_CONTEXT` ENV variable.",
            content = [
                Content(mediaType = "application/json", schema = Schema(implementation = PsiElementModel::class)),
            ],
        ) psaContext: String,
    ): CompletionModel? = null

    @Operation(
        tags = ["Methods"],
        description =
            "Generate code for file template. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa/tree/main?tab=readme-ov-file#code-templates\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "GenerateFileFromTemplate",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Template Data Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = TemplateDataModel::class))],
            ),
        ],
    )
    @POST
    @Path("GenerateFileFromTemplate")
    fun generateFileFromTemplate(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["GenerateFileFromTemplate"], defaultValue = "GenerateFileFromTemplate"),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"], defaultValue = "0"),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
        @RequestBody(
            description = "Note that body will be passed as file path to the JSON content via `PSA_CONTEXT` ENV variable.",
            content = [
                Content(mediaType = "application/json", schema = Schema(implementation = GenerateFileFromTemplateData::class)),
            ],
        ) psaContext: String,
    ): TemplateDataModel? = null

    @Operation(
        tags = ["Methods"],
        description =
            "Perform some editor action. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa/tree/main?tab=readme-ov-file#editor-actions\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "PerformEditorAction",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Editor Action result",
                content = [Content(mediaType = "text/plain")],
            ),
        ],
    )
    @POST
    @Path("PerformEditorAction")
    fun performEditorAction(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["PerformEditorAction"], defaultValue = "PerformEditorAction"),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"], defaultValue = "0"),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
        @RequestBody(
            description = "Note that body will be passed as file path to the JSON content via `PSA_CONTEXT` ENV variable.",
            content = [
                Content(mediaType = "application/json", schema = Schema(implementation = EditorActionInputModel::class)),
            ],
        ) psaContext: String,
    ) = null

    @Operation(
        tags = ["Methods"],
        description =
            "Retrieve static completions. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa/tree/main?tab=readme-ov-file#code-templates\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "GetStaticCompletions",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Static Completions Data Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = StaticCompletionsModel::class))],
            ),
        ],
    )
    @POST
    @Path("GetStaticCompletions")
    fun getStaticCompletions(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["GetStaticCompletions"], defaultValue = "GetStaticCompletions"),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"], defaultValue = "0"),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
    ) = null

    // PHP

    @Operation(
        tags = ["Methods"],
        description =
            "Retrieve type providers for PHP extension. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa/tree/main?tab=readme-ov-file#code-templates\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "GetTypeProviders",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Type Providers Data Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = TypeProvidersModel::class))],
            ),
        ],
    )
    @POST
    @Path("PHP/GetTypeProviders")
    fun getTypeProviders(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["GetTypeProviders"], defaultValue = "GetTypeProviders"),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"], defaultValue = "0"),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
    ) = null
}
