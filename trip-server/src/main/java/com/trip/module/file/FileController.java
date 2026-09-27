package com.trip.module.file;

import com.trip.common.exception.BizException;
import com.trip.common.result.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
public class FileController {
    private final Path directory;
    public FileController(@Value("${trip.upload.local-path:./uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    @PostMapping("/file/upload")
    public R<Map<String,String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024) throw new BizException(400,"图片须非空且不超过5MB");
        byte[] bytes = file.getBytes();
        String format;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BizException(400,"只支持有效的PNG或JPEG图片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png","jpeg","jpg").contains(format)) throw new BizException(400,"只支持PNG或JPEG图片");
                long width=reader.getWidth(0), height=reader.getHeight(0);
                if (width<1 || height<1 || width>6000 || height>6000 || width*height>20000000) throw new BizException(400,"图片尺寸过大，请压缩后上传");
                if (reader.read(0) == null) throw new BizException(400,"图片内容损坏");
            } finally { reader.dispose(); }
        } catch (IOException e) { throw new BizException(400,"图片内容损坏或格式不支持"); }
        String name=UUID.randomUUID().toString().replace("-", "")+(format.equals("png")?".png":".jpg");
        Files.createDirectories(directory);
        Files.write(directory.resolve(name),bytes,StandardOpenOption.CREATE_NEW);
        return R.ok(Map.of("url","/api/files/"+name,"name",name));
    }
    @GetMapping("/files/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) throws IOException {
        if (!name.matches("[a-f0-9]{32}\\.(png|jpg)")) return ResponseEntity.notFound().build();
        Path file=directory.resolve(name).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().header("X-Content-Type-Options","nosniff")
                .contentType(name.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG)
                .contentLength(Files.size(file)).body(new FileSystemResource(file));
    }
}
