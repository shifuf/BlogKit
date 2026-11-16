package com.blog.system.web.controller;

import com.blog.system.service.ArticleMediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
public class ArticleMediaController {
    private final ArticleMediaService media;
    public ArticleMediaController(ArticleMediaService media){this.media=media;}
    @GetMapping("/api/v1/article-media/open")
    public ResponseEntity<Void> open(@RequestParam String cap){
        return ResponseEntity.status(302).header("Cache-Control","private, no-store")
                .location(URI.create(media.open(cap))).build();
    }
}
