package com.eventhub.auth.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.eventhub.auth.dto.request.UpdateUserProfileRequest;
import com.eventhub.auth.dto.request.UserProfileRequest;
import com.eventhub.auth.dto.response.UserProfileResponse;
import com.eventhub.auth.entity.UserProfile;

// component Model = "spring" để MapStruct tạo ra một bean Spring cho mapper này, giúp chúng ta có thể inject mapper vào các service hoặc controller.
@Mapper (componentModel = "spring")
public interface UserProfileMapper {
    // MapStruct sẽ tự động tạo ra các phương thức chuyển đổi giữa Entity và DTO

    // Entity -> DTO
    UserProfileResponse toResponse(UserProfile userProfile);

    // DTO -> Entity
    UserProfile toEntity(UserProfileRequest userProfileRequest);

    // Cập nhật Entity hiện tại từ Update Request DTO
    // DTO -> Entity (update) (chỉ cập nhật các trường không null từ DTO sang Entity)
    // @BeanMapping (nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE) để MapStruct bỏ qua các trường null trong DTO khi cập nhật Entity.
    // @MappingTarget UserProfile userProfile để MapStruct biết rằng chúng ta muốn cập nhật đối tượng UserProfile hiện tại thay vì tạo một đối tượng mới.
    @BeanMapping (nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateUserProfileRequest request, @MappingTarget UserProfile userProfile);


}