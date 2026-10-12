package com.telme.global.common;

import java.time.LocalDate;

public interface DailyCount {

    LocalDate getDay();
    
    long getCount();
}
