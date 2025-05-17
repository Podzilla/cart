package service;

import cart.exception.GlobalHandlerException;
import cart.model.PromoCode;
import cart.repository.PromoCodeRepository;
import cart.service.PromoCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @InjectMocks
    private PromoCodeService promoCodeService;

    private PromoCode promoCode;

    @BeforeEach
    void setUp() {
        promoCode = new PromoCode();
        promoCode.setId("promo1");
        promoCode.setCode("SAVE10");
        promoCode.setDescription("10% off");
        promoCode.setDiscountType(PromoCode.DiscountType.PERCENTAGE);
        promoCode.setDiscountValue(new BigDecimal("10.00"));
        promoCode.setActive(true);
        promoCode.setExpiryDate(Instant.now().plusSeconds(3600));
        promoCode.setMinimumPurchaseAmount(new BigDecimal("50.00"));
    }

    @Test
    void createOrUpdatePromoCode_newPromoCode_createsAndSaves() {
        PromoCode input = new PromoCode();
        input.setCode("NEWCODE");
        input.setDiscountType(PromoCode.DiscountType.FIXED_AMOUNT);
        input.setDiscountValue(new BigDecimal("5.00"));
        input.setActive(true);

        when(promoCodeRepository.findByCode("NEWCODE")).thenReturn(Optional.empty());
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = promoCodeService.createOrUpdatePromoCode(input);

        assertEquals("NEWCODE", result.getCode());
        assertEquals(PromoCode.DiscountType.FIXED_AMOUNT, result.getDiscountType());
        assertEquals(new BigDecimal("5.00"), result.getDiscountValue());
        assertTrue(result.isActive());
        verify(promoCodeRepository).findByCode("NEWCODE");
        verify(promoCodeRepository).save(input);
    }

    @Test
    void createOrUpdatePromoCode_existingPromoCode_updatesAndSaves() {
        PromoCode input = new PromoCode();
        input.setCode("save10"); // Mixed case to test uppercase conversion
        input.setDiscountType(PromoCode.DiscountType.FIXED_AMOUNT);
        input.setDiscountValue(new BigDecimal("15.00"));
        input.setActive(false);

        when(promoCodeRepository.findByCode("SAVE10")).thenReturn(Optional.of(promoCode));
        when(promoCodeRepository.save(any(PromoCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromoCode result = promoCodeService.createOrUpdatePromoCode(input);

        assertEquals("promo1", result.getId()); // Preserves existing ID
        assertEquals("SAVE10", result.getCode());
        assertEquals(PromoCode.DiscountType.FIXED_AMOUNT, result.getDiscountType());
        assertEquals(new BigDecimal("15.00"), result.getDiscountValue());
        assertFalse(result.isActive());
        verify(promoCodeRepository).findByCode("SAVE10");
        verify(promoCodeRepository).save(input);
    }

    @Test
    void findByCode_existingCode_returnsPromoCode() {
        when(promoCodeRepository.findByCode("SAVE10")).thenReturn(Optional.of(promoCode));

        Optional<PromoCode> result = promoCodeService.findByCode("save10"); // Mixed case

        assertTrue(result.isPresent());
        assertEquals(promoCode, result.get());
        verify(promoCodeRepository).findByCode("SAVE10");
    }

    @Test
    void findByCode_nonExistentCode_returnsEmpty() {
        when(promoCodeRepository.findByCode("FAKECODE")).thenReturn(Optional.empty());

        Optional<PromoCode> result = promoCodeService.findByCode("fakecode");

        assertTrue(result.isEmpty());
        verify(promoCodeRepository).findByCode("FAKECODE");
    }

    @Test
    void findAll_promoCodesExist_returnsList() {
        List<PromoCode> promoCodes = List.of(promoCode);
        when(promoCodeRepository.findAll()).thenReturn(promoCodes);

        List<PromoCode> result = promoCodeService.findAll();

        assertEquals(1, result.size());
        assertEquals(promoCode, result.get(0));
        verify(promoCodeRepository).findAll();
    }

    @Test
    void findAll_noPromoCodes_returnsEmptyList() {
        when(promoCodeRepository.findAll()).thenReturn(Collections.emptyList());

        List<PromoCode> result = promoCodeService.findAll();

        assertTrue(result.isEmpty());
        verify(promoCodeRepository).findAll();
    }

    @Test
    void deletePromoCode_existingCode_deletesPromoCode() {
        when(promoCodeRepository.findByCode("SAVE10")).thenReturn(Optional.of(promoCode));
        doNothing().when(promoCodeRepository).delete(promoCode);

        promoCodeService.deletePromoCode("save10");

        verify(promoCodeRepository).findByCode("SAVE10");
        verify(promoCodeRepository).delete(promoCode);
    }

    @Test
    void deletePromoCode_nonExistentCode_throwsGlobalHandlerException() {
        when(promoCodeRepository.findByCode("FAKECODE")).thenReturn(Optional.empty());

        GlobalHandlerException ex = assertThrows(GlobalHandlerException.class,
                () -> promoCodeService.deletePromoCode("fakecode"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Promo code not found: fakecode", ex.getMessage());
        verify(promoCodeRepository).findByCode("FAKECODE");
        verify(promoCodeRepository, never()).delete(any());
    }

    @Test
    void getActivePromoCode_activePromoCode_returnsPromoCode() {
        when(promoCodeRepository.findByCode("SAVE10")).thenReturn(Optional.of(promoCode));

        Optional<PromoCode> result = promoCodeService.getActivePromoCode("save10");

        assertTrue(result.isPresent());
        assertEquals(promoCode, result.get());
        verify(promoCodeRepository).findByCode("SAVE10");
    }

    @Test
    void getActivePromoCode_inactivePromoCode_returnsEmpty() {
        PromoCode inactivePromo = new PromoCode();
        inactivePromo.setCode("INACTIVE");
        inactivePromo.setActive(false);
        when(promoCodeRepository.findByCode("INACTIVE")).thenReturn(Optional.of(inactivePromo));

        Optional<PromoCode> result = promoCodeService.getActivePromoCode("inactive");

        assertTrue(result.isEmpty());
        verify(promoCodeRepository).findByCode("INACTIVE");
    }

    @Test
    void getActivePromoCode_nonExistentCode_returnsEmpty() {
        when(promoCodeRepository.findByCode("FAKECODE")).thenReturn(Optional.empty());

        Optional<PromoCode> result = promoCodeService.getActivePromoCode("fakecode");

        assertTrue(result.isEmpty());
        verify(promoCodeRepository).findByCode("FAKECODE");
    }
}
