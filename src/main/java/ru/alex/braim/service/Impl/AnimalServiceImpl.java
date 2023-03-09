package ru.alex.braim.service.Impl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.alex.braim.annotation.Id;
import ru.alex.braim.dto.AnimalDto;
import ru.alex.braim.dto.AnimalProjection;
import ru.alex.braim.dto.OldAndNewTypes;
import ru.alex.braim.entity.*;
import ru.alex.braim.exception.AlreadyExistException;
import ru.alex.braim.exception.ConnectionWithAnimal;
import ru.alex.braim.exception.IncompatibleData;
import ru.alex.braim.exception.NotFoundException;
import ru.alex.braim.mapper.AnimalMapper;
import ru.alex.braim.repository.AnimalRepository;
import ru.alex.braim.requestParam.AnimalRequestParams;
import ru.alex.braim.service.*;
import ru.alex.braim.utils.enums.LifeStatusEnum;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Validated
public class AnimalServiceImpl implements AnimalService {

    private final AnimalRepository animalRepository;
    private final AnimalTypeService animalTypeService;
    private final AccountService accountService;

    private final LocationService locationService;

    private final AnimalMapper animalMapper;

    @Override
    @Transactional
    public AnimalProjection getAnimalById(@Id Long id) {

        if (!animalExistById(id)) {
            throw new NotFoundException("animal with id = " + id + " no found");
        }

        return animalRepository.getAnimalProjectionById(id);
    }

    @Override
    @Transactional
    public AnimalProjection createAnimal(@Valid AnimalDto animalDto) {
        Animal savedAnimal = saveAnimal(animalDto);

        return animalRepository.getAnimalProjectionById(savedAnimal.getId());
    }


    private Animal saveAnimal(AnimalDto animalDto) {
        List<AnimalType> animalType = animalTypeService.getAnimalTypeList(animalDto.getAnimalTypes());
        Account account = accountService.getAccountEntityById(Long.valueOf(animalDto.getChipperId()));
        LocationInfo locationInfo = locationService.getLocationEntityById(animalDto.getChippingLocationId());

        Animal newAnimal = animalMapper.toEntity(animalDto);
        ChippingInfo chippingInfo = new ChippingInfo();
        chippingInfo.setChipper(account);
        chippingInfo.setLocationInfo(locationInfo);

        newAnimal.setAnimalTypeList(animalType);
        newAnimal.setLifeStatus(LifeStatusEnum.ALIVE.getLifeStatus());
        newAnimal.setChippingInfo(chippingInfo);

        chippingInfo.setAnimal(newAnimal);

        return animalRepository.save(newAnimal);
    }

    @Override
    @Transactional
    public AnimalProjection updateAnimal(@Valid AnimalDto animalDto, @Id Long id) {
        Account account = accountService.getAccountEntityById(Long.valueOf(animalDto.getChipperId()));
        LocationInfo locationInfo = locationService.getLocationEntityById(animalDto.getChippingLocationId());
        Animal animal = getAnimalEntityById(id);

        if (setAliveForDeadAnimal(animalDto, animal)) {
            throw new IncompatibleData();
        }

        if (animal.getAnimalLocations().size() != 0 && Objects.equals(animal.getAnimalLocations().get(0).getId(), animalDto.getChippingLocationId())) {
            throw new IncompatibleData();
        }

        animal.setGender(animalDto.getGender());
        animal.setHeight(animal.getHeight());
        animal.setWeight(animal.getWeight());

        ChippingInfo newChippingInfo = new ChippingInfo();
        newChippingInfo.setChipper(account);
        newChippingInfo.setLocationInfo(locationInfo);

        animal.setChippingInfo(newChippingInfo);

        animal.setLifeStatus(animalDto.getLifeStatus());

        if (isDie(animalDto, animal)) {
            animal.getChippingInfo().setDeathDateTime(new Timestamp(System.currentTimeMillis()));
        }

//        animalRepository.save(updateAnimal);

        return animalRepository.getAnimalProjectionById(animal.getId());
    }

    private static boolean isDie(AnimalDto animalDto, Animal animal) {
        return animalDto.getLifeStatus().equals(LifeStatusEnum.DEAD.getLifeStatus())
                && animal.getChippingInfo().getChippingDateTime() == null;
    }

    @Override
    @Transactional
    public AnimalProjection addTypeToAnimal(@Id Long animalId, @Id Long typeId) {
        Animal animal = getAnimalEntityById(animalId);
        AnimalType animalType = animalTypeService.getAnimalTypeEntityById(typeId);

        throwIfTypeAlreadyExist(animal, animalType);

        animal.getAnimalTypeList().add(animalType);
        animalRepository.save(animal);

        return animalRepository.getAnimalProjectionById(animalId);
    }

    private static void throwIfTypeAlreadyExist(Animal animal, AnimalType animalType) {
        if (animal.getAnimalTypeList().contains(animalType)) {
            throw new AlreadyExistException("");
        }
    }

    @Override
    @Transactional
    public AnimalProjection changeTypeAnimal(@Id Long animalId, @Valid OldAndNewTypes oldAndNewTypes) {
        Animal animal = getAnimalEntityById(animalId);
        AnimalType oldAnimalType = animalTypeService.getAnimalTypeEntityById(oldAndNewTypes.getOldType());
        AnimalType newAnimalType = animalTypeService.getAnimalTypeEntityById(oldAndNewTypes.getNewType());

        int oldTypeIndex = getOldTypeIndex(animal, oldAnimalType);

        if (oldTypeIndex == -1) {
            throw new NotFoundException("");
        }

        throwIfTypeAlreadyExist(animal, newAnimalType);
        animal.getAnimalTypeList().set(oldTypeIndex, newAnimalType);
        animalRepository.save(animal);

        return animalRepository.getAnimalProjectionById(animalId);
    }

    private static int getOldTypeIndex(Animal animal, AnimalType oldAnimalType) {
        int oldTypeIndex = animal.getAnimalTypeList().indexOf(oldAnimalType);

        if (oldTypeIndex == -1) {
            throw new NotFoundException("");
        }

        return oldTypeIndex;
    }

    @Override
    @Transactional
    public AnimalProjection deleteTypeFromAnimal(@Id Long animalId, @Id Long typeId) {
        Animal animal = getAnimalEntityById(animalId);
        AnimalType animalType = animalTypeService.getAnimalTypeEntityById(typeId);

        if (animal.getAnimalTypeList().size() == 1 && animal.getAnimalTypeList().get(0).equals(animalType)) {
            throw new IncompatibleData();
        }

        int oldTypeIndex = getOldTypeIndex(animal, animalType);
        animal.getAnimalTypeList().remove(oldTypeIndex);
        animalRepository.save(animal);

        return animalRepository.getAnimalProjectionById(animalId);
    }

    @Override
    @Transactional
    public void deleteAnimal(Long id) {
        Animal animal = getAnimalEntityById(id);

        if (!animal.getAnimalLocations().isEmpty()) {
            throw new ConnectionWithAnimal("");
        }

        animalRepository.delete(animal);
    }

    private static boolean setAliveForDeadAnimal(AnimalDto animalDto, Animal animal) {
        return animal.getLifeStatus().equals(LifeStatusEnum.DEAD.getLifeStatus())
                && animalDto.getLifeStatus().equals(LifeStatusEnum.ALIVE.getLifeStatus());
    }

    @Override
    @Transactional
    public List<AnimalProjection> getAnimalListByParams(@Valid AnimalRequestParams animalDtoSpecification) {

        Pageable pageable = PageRequest.of(animalDtoSpecification.getFrom(), animalDtoSpecification.getSize());

        Page<AnimalProjection> animalProjectionList = animalRepository.
                findAnimalProjectionByParams(animalDtoSpecification, pageable);

        return animalProjectionList.getContent();
    }

    @Override
    @Transactional
    public boolean animalExistById(@Id Long id) {
        return animalRepository.existsById(id);
    }

    @Override
    public void flushAnimal() {
        animalRepository.flush();
    }

    @Override
    @Transactional
    public Animal getAnimalEntityById(@Id Long id) {
        return animalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("animal with id = " + id + " not found"));
    }

}
