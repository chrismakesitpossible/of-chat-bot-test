package com.ofchatbot.service;

import com.ofchatbot.entity.ErrorLog;
import com.ofchatbot.repository.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorLogService {
    
    private final ErrorLogRepository errorLogRepository;
    
    public void logError(String errorType, String errorMessage, Exception exception, String context) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setErrorType(errorType);
        errorLog.setErrorMessage(errorMessage);
        errorLog.setContext(context);
        
        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            errorLog.setStackTrace(sw.toString());
        }
        
        errorLogRepository.save(errorLog);
        log.error("Error logged to database: {} - {}", errorType, errorMessage);
    }
    
    public void logError(String errorType, String errorMessage, String context) {
        logError(errorType, errorMessage, null, context);
    }
}
