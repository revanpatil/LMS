package com.example.demo.service;

import com.example.demo.dto.InitiateTransactionRequest;
import com.example.demo.models.*;
import com.example.demo.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TransactionService{

    @Autowired
    StudentService studentService;

    @Autowired
    BookService bookService;

    @Autowired
    AdminService adminService;

    @Autowired
    TransactionRepository transactionRepository;

    @Value("${student.allowed.max-books}") //application.properties
    Integer maxBookAllowed;

    @Value("${student.allowed.duration}") //application.properties
    Integer allowedDuration;


    public String initiateTransaction(InitiateTransactionRequest initiateTransactionRequest) throws Exception {
        return initiateTransactionRequest.getTransactionType() == TransactionType.RETURN
                ? returnBook(initiateTransactionRequest)
                : issueBook(initiateTransactionRequest);
    }

    /** Issue of a book -> {studentId,bookId,adminId,transaction Type}
     *  1. Validate the request -> if the book is availaible or not, student is valid or not, admin is avaliable or not
     *  2. Validate if the book is availaible -> If the book is already issued on someone's name
     *  3. Validate if the book can be issued -> We need to check if the student has availaible limit(issue limit) on his amount or not
     *  4. Entry in the transaction
     *  5. Book to be assigned to a student => update Student column in the book table
     */


    public String issueBook(InitiateTransactionRequest initiateTransactionRequest) throws Exception {
        List<Student> studentList = studentService.findByStudent("rollNumber",initiateTransactionRequest.getStudentRollNumber());
        Student student = studentList.size() > 0 ? studentList.get(0) : null;

        List<Book> bookList = bookService.findByBook("id",String.valueOf(initiateTransactionRequest.getBookId()));
        Book book = bookList.size() > 0 ? bookList.get(0) : null;

        Admin admin = adminService.find(initiateTransactionRequest.getAdminId());

        // 1. Validate the request
        if(student == null || book == null || admin == null){
            throw new Exception("Invalid Request");
        }
        //2. Validate if the book is availaible
        if(book.getStudent()!= null){
            throw new Exception("The book is already assigned to " +book.getStudent().getRollNumber());
        }

        //3. Validate if the book can be issued to the given studnet
        if(student.getBookList().size() >= maxBookAllowed){
            throw new Exception("Issue limit reached for this student");
        }


        Transaction transaction = null;

        try {
            transaction = Transaction.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .student(student)
                    .book(book)
                    .admin(admin)
                    .transactionStatus(TransactionStatus.PENDING)
                    .transactionType(TransactionType.ISSUE)
                    .build();

            //4.  Entry in the transaction table with status pending
            transactionRepository.save(transaction);

            // 5. book to be assigned to a student
            book.setStudent(student);
            bookService.createOrUpdateBook(book);

            transaction.setTransactionStatus(TransactionStatus.SUCCESS);
        }catch(Exception e){
            transaction.setTransactionStatus(TransactionStatus.FAILURE);
            book.setStudent(null);
        }finally {
            transactionRepository.save(transaction);
        }


        return transaction.getTransactionId();
    }


    /**
     * 1. Validate the book, student, admin and also if validate if the book is issued to the same person
     * 2. Get the correspondence issue in transacation Entry in the transaction table
     * 3. Due date check, if due date - issue date > allowedDuration ==> fine calculation
     * 4. if there is no fine, de-allocate the book from student's name ==> book table
     * 5.
     */
    public String returnBook(InitiateTransactionRequest initiateTransactionRequest) throws Exception {
        List<Student> studentList = studentService.findByStudent("rollNumber",initiateTransactionRequest.getStudentRollNumber());
        Student student = studentList.size() > 0 ? studentList.get(0) : null;

        List<Book> bookList = bookService.findByBook("id",String.valueOf(initiateTransactionRequest.getBookId()));
        Book book = bookList.size() > 0 ? bookList.get(0) : null;

        Admin admin = adminService.find(initiateTransactionRequest.getAdminId());

        // 1. Validate the request
        if(student == null || book == null || admin == null){
            throw new Exception("Invalid Request");
        }

        if(book.getStudent() == null || !book.getStudent().getId().equals(student.getId())){
            throw new Exception("This book isn't assigned to the particualar student");
        }

        //2. get the corresponding issuance Transaction
        Transaction issuanceTransaction = transactionRepository.findTopByStudentAndBookAndTransactionTypeOrderByIdDesc(student,book,TransactionType.ISSUE);

        if(issuanceTransaction == null){
            throw new Exception("This book hasn't been issued by anyone");
        }

        Transaction transaction = null;
        try{
            Integer fine = calculateFine(issuanceTransaction.getCreatedOn());

            transaction = Transaction.builder().
                    transactionId(UUID.randomUUID().toString())
                    .transactionType(initiateTransactionRequest.getTransactionType())
                    .transactionStatus(TransactionStatus.PENDING)
                    .student(student)
                    .book(book)
                    .admin(admin)
                    .fine(fine)
                    .build();

            transactionRepository.save(transaction);

            // payFine

            if(fine==0){
                book.setStudent(null);
                bookService.createOrUpdateBook(book);
                transaction.setTransactionStatus(TransactionStatus.SUCCESS);
            }

        }catch (Exception e){
            transaction.setTransactionStatus(TransactionStatus.FAILURE);
        }finally {
            transactionRepository.save(transaction);
        }

        return transaction.getTransactionId();
    }

    private Integer calculateFine(Date issuanceTime){
        long issuanceTimeInMillis = issuanceTime.getTime();
        long currentTime = System.currentTimeMillis();

        long diff = currentTime - issuanceTimeInMillis;
        long daysPassed = TimeUnit.DAYS.convert(diff,TimeUnit.MILLISECONDS);

        if(daysPassed > allowedDuration){
            return (int)(daysPassed-allowedDuration);
        }

        return 0;
    }

    public void payFine(Integer amount, Integer studentId, String txnId){
        // get the return trxn from DB using txnId
        // Payment
        // Deallocate the book, mark this transaction as successful
        // save this transaction in DB
    }
}
