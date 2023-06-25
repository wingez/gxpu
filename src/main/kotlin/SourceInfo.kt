data class SourceInfo(
    val filename: String,
    val lineNumber: Int,
    val isApplicable: Boolean = true,
) {
    companion object {
        val notApplicable = SourceInfo("", 0, false)
    }
}