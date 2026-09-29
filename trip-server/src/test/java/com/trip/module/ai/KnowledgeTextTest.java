package com.trip.module.ai;
import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class KnowledgeTextTest {
    @Test void chunkBoundariesUseUnicodeCodePointsAndPreserveOverlap() {
        String text="甲😀".repeat(700);var chunks=KnowledgeText.chunks(text);
        assertEquals(3,chunks.size());assertEquals(500,chunks.get(0).content().codePointCount(0,chunks.get(0).content().length()));
        assertEquals(450,chunks.get(1).start());assertEquals(1400,chunks.get(2).end());
    }
    @Test void chunksReconstructOriginalWithoutSurrogateSplitting() {
        for(int length:List.of(1,499,500,501,950,951,20000)) {
            String text="😀".repeat(length);var chunks=KnowledgeText.chunks(text);StringBuilder rebuilt=new StringBuilder(chunks.get(0).content());
            for(int i=1;i<chunks.size();i++){var c=chunks.get(i);assertEquals(50,chunks.get(i-1).end()-c.start());rebuilt.append(c.content().substring(c.content().offsetByCodePoints(0,50)));}
            assertEquals(text,rebuilt.toString());
        }
    }
    @Test void lexicalTokensDoNotPretendToUnderstandSynonyms() {
        assertEquals(java.util.Set.of("香格","格里","里拉","十月"),KnowledgeText.tokens("香格里拉 十月"));
        assertEquals(java.util.Set.of("hiking","yunnan"),KnowledgeText.tokens("HIKING in YUNNAN"));
        assertTrue(KnowledgeText.tokens("😀?!").isEmpty());assertFalse(KnowledgeText.tokens("徒步").contains("hiking"));
    }
    @Test void inputNormalizationAndInvalidUnicodeAreExplicit() {
        assertEquals("标题\n正文",KnowledgeText.clean("\ufeff标题\r\n正文 ",1,20,"正文"));
        for(String text:List.of(" ","x\u0000y","x\ud800y","a".repeat(201)))assertThrows(BizException.class,()->KnowledgeText.clean(text,1,200,"正文"));
        assertEquals(200,KnowledgeText.clean("😀".repeat(200),1,200,"标题").codePointCount(0,400));
    }
}
