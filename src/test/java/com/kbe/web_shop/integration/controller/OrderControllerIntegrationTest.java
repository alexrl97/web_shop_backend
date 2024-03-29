package com.kbe.web_shop.integration.controller;

import com.kbe.web_shop.common.ApiResponse;
import com.kbe.web_shop.config.constants.Role;
import com.kbe.web_shop.controller.OrderController;
import com.kbe.web_shop.dto.cart.CartDto;
import com.kbe.web_shop.dto.checkout.CheckoutItemDto;
import com.kbe.web_shop.dto.checkout.StripeResponse;
import com.kbe.web_shop.model.*;
import com.kbe.web_shop.repository.CategoryRepo;
import com.kbe.web_shop.repository.OrderItemsRepo;
import com.kbe.web_shop.repository.ProductRepo;
import com.kbe.web_shop.repository.UserRepo;
import com.kbe.web_shop.service.AddressService;
import com.kbe.web_shop.service.AuthenticationService;
import com.kbe.web_shop.service.CartService;
import com.kbe.web_shop.service.OrderService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class OrderControllerIntegrationTest {

    @Autowired
    private OrderController orderController;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private AddressService addressService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderItemsRepo orderItemsRepo;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private CategoryRepo categoryRepo;

    private User user;

    private CartDto cartDto1;

    @Test
    public void testCheckOutList() throws StripeException {

        List<CheckoutItemDto> checkoutItemDtoList = new ArrayList<>();
        CheckoutItemDto checkoutItemDto= new CheckoutItemDto();
        checkoutItemDto.setPrice(100);
        checkoutItemDto.setQuantity(1);
        checkoutItemDto.setProductName("test");
        checkoutItemDto.setUserId(1);
        checkoutItemDto.setProductId(1);
        checkoutItemDtoList.add(checkoutItemDto);

        ResponseEntity<StripeResponse> stripeResponse = orderController.checkoutList(checkoutItemDtoList);
        String sessionID = stripeResponse.getBody().getSessionId();

        assertNotNull(sessionID);
        assertEquals(stripeResponse.getStatusCode(), HttpStatus.OK);
    }

    @Test
    public void testCreateOrder() throws InterruptedException {
        Order order = createNewTestingUserAndOrder();
        assertNotNull(order);
        assertEquals(order.getUser().getEmail(), user.getEmail());
        assertEquals(order.getTotalPrice(), 19.99);

        OrderItem orderItem = orderItemsRepo.findAll().stream().filter(X -> Objects.equals(X.getOrder().getId(), order.getId())).toList().get(0);

        assertEquals(orderItem.getProduct().getId(), cartDto1.getProductId());
    }

    @Test
    public void testSendOrderStorehouseUser() throws InterruptedException {
        User user = new User();
        user.setRole(Role.storehouse);
        user = userRepo.save(user);
        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        Order order = createNewTestingUserAndOrder();

        ResponseEntity<ApiResponse> apiResponse= orderController.sendOrder(authenticationToken.getToken(), order.getId(), "1234567890");
        Thread.sleep(500);

        order = orderService.getOrder(order.getId());

        assertEquals(order.getTrackingNumber(), "1234567890");
        assertEquals(order.getStatus(), "send");
        assertEquals(apiResponse.getStatusCode(), HttpStatus.OK);
    }

    @Test
    public void testSendOrderStorehouseUserEmptryTrackingNumber() throws InterruptedException {

        User user = new User();
        user.setRole(Role.storehouse);
        user = userRepo.save(user);
        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        Order order = createNewTestingUserAndOrder();

        ResponseEntity<ApiResponse> apiResponse= orderController.sendOrder(authenticationToken.getToken(), order.getId(), "");
        Thread.sleep(500);

        order = orderService.getOrder(order.getId());

        assertNull(order.getTrackingNumber());
        assertEquals(order.getStatus(), "pending");
        assertEquals(apiResponse.getStatusCode(), HttpStatus.BAD_REQUEST);

    }

    @Test
    public void testSendOrderCustomerUser() throws InterruptedException {

        User user = new User();
        user.setRole(Role.customer);
        user = userRepo.save(user);
        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        Order order = createNewTestingUserAndOrder();

        ResponseEntity<ApiResponse> apiResponse= orderController.sendOrder(authenticationToken.getToken(), order.getId(), "1234567890");
        Thread.sleep(500);

        order = orderService.getOrder(order.getId());

        assertNull(order.getTrackingNumber());
        assertEquals(order.getStatus(), "pending");
        assertEquals(apiResponse.getStatusCode(), HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void testGetAllOrdersForStorehouseUser() throws InterruptedException {
        User user = new User();
        user.setRole(Role.storehouse);
        user = userRepo.save(user);
        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        createNewTestingUserAndOrder();
        createNewTestingUserAndOrder();

        int orderListSize = orderController.getAllOrdersByUser(authenticationToken.getToken()).getBody().size();
        assertTrue(orderListSize >= 2);
    }

    @Test
    public void testGetAllOrdersForCustomerUser() throws InterruptedException {
        user = new User();
        Random random = new Random();
        String generatedString = random.ints(92, 123 + 1)
                .limit(10)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        String email = generatedString + "@mail.de";
        user.setEmail(email);
        user.setRole(Role.customer);
        userRepo.save(user);
        user = userRepo.findByEmail(email);

        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        Address address = new Address();
        address.setUser(user);
        addressService.createUpdateAddress(address);

        cartDto1 = new CartDto();
        cartDto1.setId(1000);
        cartDto1.setQuantity(1);
        cartDto1.setUser(user);
        cartDto1.setProductId(getTestProduct().getId());
        cartService.addToCart(cartDto1);

        orderController.createOrder(authenticationToken.getToken(), "sessionId");

        Thread.sleep(500);

        int orderListSize = orderController.getAllOrdersByUser(authenticationToken.getToken()).getBody().size();
        assertEquals(1, orderListSize);
    }

    @Test
    public void testGetOrderByValidID() throws InterruptedException {
        User user = new User();
        user.setRole(Role.storehouse);
        user = userRepo.save(user);
        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        Order order = createNewTestingUserAndOrder();

        ResponseEntity<Object> apiResponse= orderController.getOrderById(order.getId(), authenticationToken.getToken());
        assertEquals(apiResponse.getStatusCode(), HttpStatus.OK);
    }

    @Test
    public void testGetOrderByInValidID() throws InterruptedException {
        User user = new User();
        user.setRole(Role.storehouse);
        user = userRepo.save(user);
        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        ResponseEntity<Object> apiResponse= orderController.getOrderById(Integer.MAX_VALUE, authenticationToken.getToken());
        assertEquals(apiResponse.getStatusCode(), HttpStatus.NOT_FOUND);
    }


    private Order createNewTestingUserAndOrder() throws InterruptedException {
        user = new User();
        Random random = new Random();
        String generatedString = random.ints(92, 123 + 1)
                .limit(10)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        String email = generatedString + "@mail.de";
        user.setEmail(email);
        userRepo.save(user);
        user = userRepo.findByEmail(email);

        AuthenticationToken authenticationToken = new AuthenticationToken(user);
        authenticationService.saveConfirmationToken(authenticationToken);

        Address address = new Address();
        address.setUser(user);
        addressService.createUpdateAddress(address);

        cartDto1 = new CartDto();
        cartDto1.setId(1000);
        cartDto1.setQuantity(1);
        cartDto1.setUser(user);
        cartDto1.setProductId(getTestProduct().getId());
        cartService.addToCart(cartDto1);

        orderController.createOrder(authenticationToken.getToken(), "sessionId");

        Thread.sleep(500);
        return orderService.listOrders(user).get(0);
    }

    private Product getTestProduct(){
        Category category = new Category();
        category.setCategoryName("Test Category");
        category.setId(100);
        category = categoryRepo.findFirstByOrderByIdDesc();

        Product product1 = new Product();
        product1.setName("Product 1");
        product1.setDescription("Description 1");
        product1.setImageURL("image1.jpg");
        product1.setPrice(19.99);
        product1.setDeckCardId("12345");
        product1.setRarity("Common");
        product1.setCategory(category);
        productRepo.save(product1);

        List<Product> allProducts = productRepo.findAll();

        return allProducts.stream()
                .filter(p -> p.getName().equals("Product 1"))
                .findFirst()
                .orElse(null);
    }
}