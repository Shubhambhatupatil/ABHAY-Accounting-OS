package com.anvritai.abhay.api;
import jakarta.validation.constraints.*; import java.time.Instant; import java.util.UUID;
public final class SettingsDtos { private SettingsDtos(){}
 public record SettingRequest(@NotBlank @Size(max=120) String key,@NotBlank @Size(max=4000) String value,@NotBlank String valueType){}
 public record SettingResponse(UUID id,String category,String key,String value,String valueType,Instant updatedAt){}
}
