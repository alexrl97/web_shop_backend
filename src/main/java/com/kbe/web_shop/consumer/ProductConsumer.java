package com.kbe.web_shop.consumer;

import com.kbe.web_shop.dto.product.ProductDto;
import com.kbe.web_shop.repository.CategoryRepo;
import com.kbe.web_shop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductConsumer.class);

    @Autowired
    ProductService productService;

    @Autowired
    CategoryRepo categoryRepo;


    @RabbitListener(queues = {"${product_create_queue}"})
    public void consumeCreateMessage(String message){
        LOGGER.info(String.format("Received create message for product -> %s", message));
        ProductDto productDto = ProductDto.fromJsonString(message);
        productService.createProduct(productDto, categoryRepo.findById(productDto.getCategoryId()).orElse(null));
    }

    @RabbitListener(queues = {"${product_update_queue}"})
    public void consumeUpdateMessage(String message) throws Exception {
        LOGGER.info(String.format("Received update message for product -> %s", message));
        ProductDto productDto = ProductDto.fromJsonString(message);
        productService.updateProduct(productDto, productDto.getId());
    }

    @RabbitListener(queues = {"${product_delete_queue}"})
    public void consumeDeleteMessage(String message) throws Exception {
        LOGGER.info(String.format("Received delete message for product -> %s", message));
        productService.deleteProduct(Integer.parseInt(message));
    }
}
