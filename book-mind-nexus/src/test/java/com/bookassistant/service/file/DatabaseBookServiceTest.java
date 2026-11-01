package com.bookassistant.service.file;

import com.bookassistant.entity.Book;
import com.bookassistant.repository.BookRepository;
import com.bookassistant.service.infrastructure.BookPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class PersistentBookStoreTest {

    private BookRepository bookRepository;
    private BookPersistenceService bookPersistenceService;
    private PersistentBookStore persistentBookStore;

    @BeforeEach
    void setUp() {
        bookRepository = Mockito.mock(BookRepository.class);
        bookPersistenceService = Mockito.mock(BookPersistenceService.class);
        persistentBookStore = new PersistentBookStore(bookRepository, bookPersistenceService);
    }

    @Test
    void testSaveBook() {
        Mockito.when(bookPersistenceService.save(Mockito.any(Book.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        Book saved = persistentBookStore.saveBook(1L, "Test Book", "test.pdf", 10, "Full text here");

        assertNotNull(saved);
        assertEquals("Test Book", saved.getTitle());
        assertEquals("test.pdf", saved.getFileName());
        assertEquals(10, saved.getPageCount());
        assertEquals("Full text here", saved.getFullText());
    }

    @Test
    void testFindUserBooks() {
        Book book = new Book();
        book.setUserId(1L);

        Mockito.when(bookRepository.findByUserId(1L)).thenReturn(List.of(book));

        List<Book> books = persistentBookStore.findUserBooks(1L);
        assertEquals(1, books.size());
        assertEquals(1L, books.get(0).getUserId());
    }

    @Test
    void testFindById() {
        Book book = new Book();
        book.setUserId(1L);

        Mockito.when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        Optional<Book> found = persistentBookStore.findById(1L);
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().getUserId());
    }

    @Test
    void testFindById_NotFound() {
        Mockito.when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Book> found = persistentBookStore.findById(99L);
        assertTrue(found.isEmpty());
    }

    @Test
    void testDeleteBook() {
        persistentBookStore.deleteBook(1L);
        Mockito.verify(bookRepository).deleteById(1L);
    }
}
