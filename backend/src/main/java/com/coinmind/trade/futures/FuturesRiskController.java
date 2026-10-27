package com.coinmind.trade.futures;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/futures")
public class FuturesRiskController {
    private final FuturesRiskCalculator calculator;

    public FuturesRiskController(FuturesRiskCalculator calculator) {
        this.calculator = calculator;
    }

    @PostMapping("/risk/preview")
    public FuturesRiskCalculator.Result preview(@RequestBody FuturesRiskCalculator.Request request) {
        return calculator.calculate(request);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse invalid(IllegalArgumentException exception) {
        return new ErrorResponse("INVALID_RISK_INPUT", exception.getMessage());
    }

    public record ErrorResponse(String code, String message) {}
}
