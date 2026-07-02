package com.anvritai.abhay.api;
import jakarta.validation.constraints.*; import java.time.Instant; import java.util.Map; import java.util.UUID;
public final class JobDtos { private JobDtos(){}
 public record EnqueueJobRequest(@NotBlank String jobType, Map<String,Object> payload, Instant scheduledAt,
        @Min(1) @Max(10) Integer maxAttempts){}
 public record JobResponse(UUID id,String jobType,String status,Map<String,Object> payload,Map<String,Object> result,
        int attempts,int maxAttempts,Instant scheduledAt,Instant startedAt,Instant completedAt,String correlationId){}
}
