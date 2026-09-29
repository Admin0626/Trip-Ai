package com.trip.module.ai;

import com.trip.common.exception.BizException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic local lexical retrieval. Scores are query token coverage, never embedding similarity. */
public final class KnowledgeText {
    private KnowledgeText() {}
    private static final Pattern WORDS=Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> STOP=Set.of("请问","什么","如何","怎么","一下","介绍","我想","想去","知道","可以","是否","推荐");
    public record Chunk(int index,int start,int end,String content) {}
    public static String clean(String raw,int minimum,int maximum,String label) {
        if(raw==null)throw new BizException(400,label+"不能为空");
        String text=raw.replace("\r\n","\n").replace('\r','\n');
        if(text.startsWith("\ufeff"))text=text.substring(1);
        text=text.strip();int size=text.codePointCount(0,text.length());
        if(size<minimum || size>maximum)throw new BizException(400,label+"须为"+minimum+"—"+maximum+"个字符");
        if(text.codePoints().anyMatch(c->(Character.isISOControl(c)&&c!='\n'&&c!='\t')||(c>=0xd800&&c<=0xdfff)))throw new BizException(400,label+"含不支持的控制字符或无效Unicode");
        return text;
    }
    public static List<Chunk> chunks(String content) {
        int[] cps=content.codePoints().toArray();List<Chunk> chunks=new ArrayList<>();
        for(int start=0;start<cps.length;start+=450) {
            int end=Math.min(start+500,cps.length);
            chunks.add(new Chunk(chunks.size()+1,start,end,new String(cps,start,end-start)));
            if(end==cps.length)break;
        }
        return chunks;
    }
    public static Set<String> tokens(String text) {
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        var matcher=WORDS.matcher(normalized);Set<String> result=new LinkedHashSet<>();
        while(matcher.find()) {
            String term=matcher.group();int[] chars=term.codePoints().toArray();
            if(Character.UnicodeScript.of(chars[0])==Character.UnicodeScript.HAN) {
                for(int i=0;i<chars.length-1;i++){String token=new String(chars,i,2);if(!STOP.contains(token))result.add(token);}
            } else if(term.length()>=2 && term.length()<=64 && !Set.of("the","and","for","to","is","of","in").contains(term))result.add(term);
        }
        return result;
    }
}
