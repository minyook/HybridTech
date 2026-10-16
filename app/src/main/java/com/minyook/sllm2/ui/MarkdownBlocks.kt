package com.minyook.sllm2.ui

internal sealed interface MarkdownBlock {
    data class Line(val text:String):MarkdownBlock
    data class Code(val text:String):MarkdownBlock
    data class Table(val rows:List<List<String>>):MarkdownBlock
}

internal fun markdownBlocks(markdown:String):List<MarkdownBlock> {
    val lines=markdown.lines()
    val result=mutableListOf<MarkdownBlock>()
    var index=0
    fun cells(line:String)=line.trim().removePrefix("|").removeSuffix("|").split(Regex("(?<!\\\\)\\|")).map{it.trim().replace("\\|","|")}
    while(index<lines.size) {
        val line=lines[index]
        if(line.trimStart().startsWith("```")) {
            val body=mutableListOf<String>();index++
            while(index<lines.size&&!lines[index].trimStart().startsWith("```"))body+=lines[index++]
            if(index<lines.size)index++
            result+=MarkdownBlock.Code(body.joinToString("\n"));continue
        }
        if(index+1<lines.size&&line.contains('|')) {
            val header=cells(line);val separator=cells(lines[index+1])
            if(header.size>1&&separator.size==header.size&&separator.all{it.matches(Regex(":?-{3,}:?"))}) {
                val rows=mutableListOf(header);index+=2
                while(index<lines.size&&lines[index].contains('|')&&lines[index].isNotBlank()){
                    val row=cells(lines[index]);rows+=List(header.size){row.getOrElse(it){""}};index++
                }
                result+=MarkdownBlock.Table(rows);continue
            }
        }
        result+=MarkdownBlock.Line(line);index++
    }
    return result
}
