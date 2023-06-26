abstract class CompilerError(message: String, private val sourceInfo: SourceInfo) : Error(message) {

    fun getLine(sourceProvider: SourceProvider): String {
        require(sourceInfo.isApplicable)
        return formatSource(sourceProvider, sourceInfo)
    }
}

data class SourceInfo(
    val filename: String,
    val lineNumber: Int,
    val isApplicable: Boolean = true,
) {
    companion object {
        val notApplicable = SourceInfo("", 0, false)
    }
}

fun interface SourceProvider {
    fun getLine(filename: String, lineNumber: Int): String
}

fun formatSource(provider: SourceProvider, sourceInfo: SourceInfo): String {
    return provider.getLine(sourceInfo.filename, sourceInfo.lineNumber)
}