package com.kbe.web_shop.consumer;

import com.kbe.web_shop.model.Category;
import com.kbe.web_shop.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CategoryConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryConsumer.class);

    @Autowired
    CategoryService categoryService;

    @RabbitListener(queues = {"${category_create_queue}"})
    public void consumeCreateMessage(String message) {
        LOGGER.info(String.format("Received create message for category -> %s", message));
        Category category = Category.fromJsonString(message);
        categoryService.createCategory(category);
    }

    @RabbitListener(queues = {"${category_update_queue}"})
    public void consumeUpdateMessage(String message) {
        LOGGER.info(String.format("Received update message for category -> %s", message));
        Category category = Category.fromJsonString(message);
        categoryService.editCategory(category.getId(), category);
    }

    @RabbitListener(queues = {"${category_delete_queue}"})
    public void consumeDeleteMessage(String message) {
        LOGGER.info(String.format("Received delete message for category -> %s", message));
        int categoryId = Integer.parseInt(message);
        categoryService.deleteCategory(categoryId);
    }
}
