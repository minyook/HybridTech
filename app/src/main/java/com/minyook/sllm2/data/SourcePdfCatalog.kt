/* 소스 안내
 * 문서 ID를 번들 PDF 파일·쪽수·판본 해시에 연결한다. PDF 렌더와 표 복원에서 같은 판본을 확인한다.
 */
package com.minyook.sllm2.data

/** Official attachments corresponding to the bundled page-by-page search text. */
internal data class SourcePdf(
    val documentId: String,
    val url: String,
    val fileName: String,
    val pageCount: Int,
    val sha256: String,
) {
    fun pageIndex(pageNumber: Int): Int? = pageNumber.takeIf { it in 1..pageCount }?.minus(1)
}

/** 검색의 documentId를 번들 PDF·쪽수·판본 해시에 연결한다. Repository와 표 복원기가 같은 판본을 확인한다. */
internal object SourcePdfCatalog {
    private val documents = listOf(
        SourcePdf(
            documentId = "confined_space_safety_guide_2022",
            url = "https://www.moel.go.kr/local/boryeong/common/downloadFile.do?bbs_id=LOCAL5&bbs_seq=20230600030&file_seq=20230600057",
            fileName = "safety-guide-2022.pdf",
            pageCount = 84,
            sha256 = "3d634673ef6a90fb3ece60fec7ef21d3d6654277aff9edef4843a7bd187eab88",
        ),
        SourcePdf(
            documentId = "confined_space_checklist_2026",
            url = "https://www.moel.go.kr/local/ansan/common/downloadFile.do?bbs_id=LOCAL1&bbs_seq=20260401450&file_seq=20260402683",
            fileName = "checklist-2026.pdf",
            pageCount = 3,
            sha256 = "3114a041b2a97d71622638044d198666ab0efcd5e4c49f40e14827036dc78998",
        ),
    ).associateBy(SourcePdf::documentId)

    fun find(documentId: String): SourcePdf? = documents[documentId]
}
