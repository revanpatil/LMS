package com.example.demo.controller;

import com.example.demo.dto.CreateBookRequest;
import com.example.demo.dto.SearchRequest;
import com.example.demo.models.Book;
import com.example.demo.service.BookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BookController {

    @Autowired
    BookService bookService;

    //Adding a book
    @PostMapping("/book")
    public void createBook(@RequestBody @Valid CreateBookRequest createBookRequest) {
        bookService.createOrUpdateBook(createBookRequest.toBook());
    }


    //Getting a list of books

    @GetMapping("/getBooks")
    public List<Book> getBooks(@RequestBody @Valid SearchRequest searchBookRequest) throws Exception {
        List<Book> list=  bookService.findByBook(searchBookRequest.getSearchKey(),searchBookRequest.getSearchValue());
        return list;
    }

    //key = author Name, value = Robert
    //Key = genre , value = PROGRAMMING
}
