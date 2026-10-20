package com.se100.clinic.catalog;

import com.se100.clinic.shared.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.se100.clinic.catalog.CatalogErrorCode.MEDICAL_ITEM_EXISTS;

@Service
@RequiredArgsConstructor
public class MedicalItemService {
  private final MedicalItemRepository medicalItemRepository;

  private static MedicalItem toEntity(CatalogDtos.MedicalItemCreateDto request){
    MedicalItem medicalItem = new MedicalItem();
    medicalItem.setName(request.name());
    medicalItem.setDescription(request.description());
    medicalItem.setPrice(request.price());
    medicalItem.setInstruction(request.instruction());
    medicalItem.setType(request.medicalItemType());
    return medicalItem;
  }
  @Transactional
  public CatalogDtos.MedicalItemResponseDto createMedicalItem(CatalogDtos.MedicalItemCreateDto request){
    if(medicalItemRepository.findByName(request.name()).isPresent()){
      throw new ConflictException(MEDICAL_ITEM_EXISTS,"Medical item with name " + request.name() + " already exists");
    }
    MedicalItem medicalItem = toEntity(request);
    medicalItemRepository.save(medicalItem);
    return CatalogDtos.MedicalItemResponseDto.from(medicalItem);
  }

  @Transactional
  public String createBatchMedicalItems(CatalogDtos.MedicalItemCreateBatchDto request){
    List<MedicalItem> medicalItems = request.items().stream().map(MedicalItemService::toEntity).toList();
    medicalItemRepository.saveAll(medicalItems);
    return "Batch medical items created successfully";
  }
}
