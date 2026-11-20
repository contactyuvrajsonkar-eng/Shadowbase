package com.internship.testcontainers_postgres_manager.exception;

import org.springframework.http.HttpStatus;

/**
 * One reusable exception for every API error.
 * It carries what the contract's error response needs: HTTP status + error code + message.
 * */

public class ApiException extends RuntimeException
{
	private final HttpStatus status;
    private final String code;
    
    public ApiException(HttpStatus status,String code,String message)
    {
     super(message);
     this.status=status;
     this.code=code;
    }
    
    //same as above but keeps the orignal technical error attached for debugging
    
    public ApiException(HttpStatus status,String code,String message,Throwable cause)
    {
    super(message,cause);
    this.status=status;
    this.code=code;
    }
    
    public HttpStatus getStatus()
    {
    return status;	
    }
    
    public String getCode()
    {
    return code;
    }
}
