package com.example.ue5analyzer.data.network

import retrofit2.http.*

/**
 * JSONPlaceholder API interface
 * Using public mock API for demonstration purposes
 */
interface JsonPlaceholderApi {
    
    /**
     * GET request to fetch all posts
     * Used as a template for fetching project templates from cloud
     */
    @GET("posts")
    suspend fun getPosts(): List<PostResponse>
    
    /**
     * GET request to fetch a single post by ID
     * Used as a template for fetching project details
     */
    @GET("posts/{id}")
    suspend fun getPostById(@Path("id") id: Int): PostResponse
    
    /**
     * GET request to fetch todos
     * Used as a template for fetching scan task status
     */
    @GET("todos")
    suspend fun getTodos(): List<TodoResponse>
    
    /**
     * POST request to create a new post
     * Used as a template for uploading scan results to cloud
     */
    @POST("posts")
    suspend fun createPost(@Body post: CreatePostRequest): PostResponse
    
    /**
     * POST request to upload asset metadata
     * Used as a template for syncing asset information
     */
    @POST("posts")
    suspend fun uploadAssetMetadata(@Body request: AssetMetadataRequest): PostResponse
}

/**
 * Response model for Post
 */
data class PostResponse(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
)

/**
 * Response model for Todo
 */
data class TodoResponse(
    val userId: Int,
    val id: Int,
    val title: String,
    val completed: Boolean
)

/**
 * Request model for creating a post
 */
data class CreatePostRequest(
    val title: String,
    val body: String,
    val userId: Int
)

/**
 * Request model for uploading asset metadata
 */
data class AssetMetadataRequest(
    val projectId: String,
    val projectName: String,
    val totalAssets: Int,
    val totalSize: Long,
    val lastScanned: Long,
    val syncStatus: String = "pending"
)
