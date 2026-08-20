package com.likelion.hackathon.domain.review.controller;

import com.likelion.hackathon.domain.review.dto.*;
import com.likelion.hackathon.domain.review.service.ReviewService;
import com.likelion.hackathon.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/meeting-logs/{id}/review-actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewActionResponse> createReviewAction(
            @PathVariable UUID id,
            @RequestBody @Valid CreateReviewActionRequest request) {
        return ApiResponse.ok(reviewService.createReviewAction(id, request));
    }

    @PostMapping("/required-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RequiredReviewResponse> createRequiredReview(@RequestParam UUID meetingLogId) {
        return ApiResponse.ok(reviewService.createRequiredReview(meetingLogId));
    }

    @GetMapping("/meetings/{id}/required-reviews")
    public ApiResponse<List<RequiredReviewResponse>> getRequiredReviews(@PathVariable UUID id) {
        return ApiResponse.ok(reviewService.getRequiredReviews(id));
    }

    @PatchMapping("/required-reviews/{id}")
    public ApiResponse<RequiredReviewResponse> confirmRequiredReview(@PathVariable UUID id) {
        return ApiResponse.ok(reviewService.confirmRequiredReview(id));
    }
}
