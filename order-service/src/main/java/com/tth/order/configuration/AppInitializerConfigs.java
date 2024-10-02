package com.tth.order.configuration;

import com.tth.commonlibrary.dto.request.order.OrderItemRequest;
import com.tth.commonlibrary.dto.request.order.OrderRequest;
import com.tth.commonlibrary.dto.response.identity.user.UserResponse;
import com.tth.commonlibrary.dto.response.inventory.InventoryItemResponse;
import com.tth.commonlibrary.dto.response.inventory.InventoryResponse;
import com.tth.commonlibrary.dto.response.product.ProductListResponse;
import com.tth.commonlibrary.enums.OrderStatus;
import com.tth.commonlibrary.enums.OrderType;
import com.tth.order.entity.Tax;
import com.tth.order.repository.OrderRepository;
import com.tth.order.repository.TaxRepository;
import com.tth.order.repository.httpclient.IdentityClient;
import com.tth.order.repository.httpclient.InventoryClient;
import com.tth.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AppInitializerConfigs {

    @Bean
    public ApplicationRunner applicationRunner(OrderService orderService, TaxRepository taxRepository, OrderRepository orderRepository, IdentityClient identityClient,
                                               InventoryClient inventoryClient) {
        return args -> {
            log.info("Initializing application.....");

            if (taxRepository.count() == 0) {
                log.info("Creating taxes.....");
                this.createTax(taxRepository);
            }

            if (orderRepository.count() == 0) {
                log.info("Creating orders.....");
                this.createOrder(orderService, identityClient, inventoryClient);
            }

            log.info("Application initialization completed.....");
        };
    }

    void createTax(TaxRepository taxRepository) {
        taxRepository.saveAll(List.of(
                Tax.builder().rate(BigDecimal.valueOf(0.01)).region("VN").build(),
                Tax.builder().rate(BigDecimal.valueOf(0.05)).region("US").build(),
                Tax.builder().rate(BigDecimal.valueOf(0.20)).region("EU").build(),
                Tax.builder().rate(BigDecimal.valueOf(0.10)).region("APAC").build(),
                Tax.builder().rate(BigDecimal.valueOf(0.15)).region("LATAM").build(),
                Tax.builder().rate(BigDecimal.valueOf(0.08)).region("MEA").build()
        ));
    }

    private void createOrder(OrderService orderService, IdentityClient identityClient, InventoryClient inventoryClient) {
        List<UserResponse> users = identityClient.listUsers().getResult();
        List<InventoryResponse> inventories = inventoryClient.listInventories(null, 1, 100).getData();
        List<InventoryItemResponse> inventoryItems = new ArrayList<>(inventories.stream()
                .map(InventoryResponse::getInventoryItems)
                .flatMap(Collection::stream)
                .toList());
        Random random = new Random();

        users.forEach(user -> IntStream.range(0, 10).forEach(index -> {
            Collections.shuffle(inventoryItems, random);
            List<ProductListResponse> randomProducts = inventoryItems.stream()
                    .map(InventoryItemResponse::getProduct).limit(3).toList();

            Set<OrderItemRequest> orderItems = randomProducts.stream()
                    .map(product -> OrderItemRequest.builder()
                            .productId(product.getId())
                            .quantity(3F)
                            .build())
                    .collect(Collectors.toSet());

            OrderRequest orderRequest = OrderRequest.builder()
                    .type(OrderType.values()[random.nextInt(OrderType.values().length)])
                    .status(OrderStatus.DELIVERED)
                    .paid(true)
                    .inventoryId(inventories.getFirst().getId())
                    .orderItems(orderItems)
                    .createdAt(this.getRandomDateTimeInYear())
                    .build();

            if (orderRequest.getType() == OrderType.OUTBOUND) {
                orderService.checkout(orderRequest, user);
            } else if (orderRequest.getType() == OrderType.INBOUND) {
                orderService.checkin(orderRequest, user);
            }
        }));
    }

    private LocalDateTime getRandomDateTimeInYear() {
        // Lấy tháng hiện tại
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();

        // Random tháng từ 1 đến tháng hiện tại
        int randomMonth = ThreadLocalRandom.current().nextInt(1, currentMonth + 1);

        // Tạo ngày bắt đầu và ngày kết thúc cho tháng random
        LocalDate start = LocalDate.of(now.getYear(), randomMonth, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        // Random ngày trong khoảng từ start đến end
        long randomDay = ThreadLocalRandom.current().nextLong(start.toEpochDay(), end.toEpochDay() + 1);

        // Chuyển đổi từ epoch day sang LocalDate và sau đó sang Date
        LocalDate randomDate = LocalDate.ofEpochDay(randomDay);
        return randomDate.atStartOfDay(ZoneId.systemDefault()).toLocalDateTime();
    }

}
